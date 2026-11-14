// Spreadsheet formula engine: tokenizer -> Pratt parser -> evaluator with lazy, memoised cell evaluation
// and cycle detection. Supports the everyday Excel subset (arithmetic, comparison, &, ranges, ~50 functions).
// Dates are ISO strings (YYYY-MM-DD); arithmetic on them works in days, like Excel serial dates.

export class FormulaError {
  constructor(code) { this.code = code }
  toString() { return this.code }
}
const ERR = {
  DIV0: new FormulaError('#DIV/0!'),
  VALUE: new FormulaError('#VALUE!'),
  REF: new FormulaError('#REF!'),
  NAME: new FormulaError('#NAME?'),
  NA: new FormulaError('#N/A'),
  NUM: new FormulaError('#NUM!'),
  CYCLE: new FormulaError('#CYCLE!'),
  PARSE: new FormulaError('#ERROR!')
}
export const isError = (v) => v instanceof FormulaError

// ---------------------------------------------------------------- A1 helpers
export function colName(c) {
  let s = ''
  let n = c + 1
  while (n > 0) { const r = (n - 1) % 26; s = String.fromCharCode(65 + r) + s; n = Math.floor((n - 1) / 26) }
  return s
}
export function colIndex(letters) {
  let n = 0
  for (const ch of letters.toUpperCase()) n = n * 26 + (ch.charCodeAt(0) - 64)
  return n - 1
}
export const cellRef = (r, c) => colName(c) + (r + 1)
export function parseRef(ref) {
  const m = /^\$?([A-Za-z]{1,3})\$?(\d+)$/.exec(ref)
  return m ? { r: parseInt(m[2], 10) - 1, c: colIndex(m[1]) } : null
}

// ---------------------------------------------------------------- dates
const DAY_MS = 86400000
const EPOCH = Date.UTC(1899, 11, 30)
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/
export const isIsoDate = (v) => typeof v === 'string' && ISO_DATE.test(v) && !Number.isNaN(Date.parse(v))
export const dateToSerial = (iso) => Math.round((Date.parse(iso + 'T00:00:00Z') - EPOCH) / DAY_MS)
export const serialToDate = (n) => new Date(EPOCH + Math.round(n) * DAY_MS).toISOString().slice(0, 10)
const todayIso = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// ---------------------------------------------------------------- literal parsing
const NUMBER_RE = /^[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?$/
/** Converts what a user typed (non-formula) into a typed value. */
export function literalValue(raw) {
  if (raw === null || raw === undefined || raw === '') return null
  const t = raw.trim()
  if (NUMBER_RE.test(t)) return parseFloat(t)
  if (/^[-+]?[\d,]+(\.\d+)?$/.test(t) && t.includes(',') && /^\d{1,3}(,\d{3})+(\.\d+)?$/.test(t.replace(/^[-+]/, ''))) {
    return parseFloat(t.replace(/,/g, ''))
  }
  if (/^-?\d+(\.\d+)?%$/.test(t)) return parseFloat(t) / 100
  const up = t.toUpperCase()
  if (up === 'TRUE') return true
  if (up === 'FALSE') return false
  return raw
}

// ---------------------------------------------------------------- tokenizer
function tokenize(src) {
  const tokens = []
  let i = 0
  while (i < src.length) {
    const ch = src[i]
    if (/\s/.test(ch)) { i++; continue }
    if (ch === '"') {
      let s = ''
      i++
      while (i < src.length) {
        if (src[i] === '"') {
          if (src[i + 1] === '"') { s += '"'; i += 2; continue }
          break
        }
        s += src[i++]
      }
      if (src[i] !== '"') throw ERR.PARSE
      i++
      tokens.push({ t: 'str', v: s })
      continue
    }
    const num = /^(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?/.exec(src.slice(i))
    if (num) { tokens.push({ t: 'num', v: parseFloat(num[0]) }); i += num[0].length; continue }
    if (src.startsWith('#REF!', i)) { tokens.push({ t: 'err', v: ERR.REF }); i += 5; continue }
    const id = /^\$?[A-Za-z_][A-Za-z0-9_.]*\$?\d*/.exec(src.slice(i))
    if (id) {
      const word = id[0]
      if (/^\$?[A-Za-z]{1,3}\$?\d+$/.test(word)) tokens.push({ t: 'ref', v: word.toUpperCase() })
      else tokens.push({ t: 'id', v: word.toUpperCase() })
      i += word.length
      continue
    }
    const two = src.slice(i, i + 2)
    if (['<=', '>=', '<>'].includes(two)) { tokens.push({ t: 'op', v: two }); i += 2; continue }
    if ('+-*/^&=<>%(),:;'.includes(ch)) { tokens.push({ t: 'op', v: ch === ';' ? ',' : ch }); i++; continue }
    throw ERR.PARSE
  }
  return tokens
}

// ---------------------------------------------------------------- parser (Pratt)
const BINARY = { '=': 1, '<>': 1, '<': 1, '>': 1, '<=': 1, '>=': 1, '&': 2, '+': 3, '-': 3, '*': 4, '/': 4, '^': 5 }

export function parse(src) {
  const tokens = tokenize(src)
  let p = 0
  const peek = () => tokens[p]
  const next = () => tokens[p++]
  const expect = (v) => {
    const tk = next()
    if (!tk || tk.v !== v) throw ERR.PARSE
  }

  function primary() {
    const tk = next()
    if (!tk) throw ERR.PARSE
    if (tk.t === 'num') return { k: 'lit', v: tk.v }
    if (tk.t === 'str') return { k: 'lit', v: tk.v }
    if (tk.t === 'err') return { k: 'lit', v: tk.v }
    if (tk.t === 'ref') {
      const a = parseRef(tk.v)
      if (peek()?.v === ':') {
        next()
        const t2 = next()
        if (!t2 || t2.t !== 'ref') throw ERR.PARSE
        const b = parseRef(t2.v)
        return { k: 'range', r1: Math.min(a.r, b.r), c1: Math.min(a.c, b.c), r2: Math.max(a.r, b.r), c2: Math.max(a.c, b.c) }
      }
      return { k: 'ref', r: a.r, c: a.c }
    }
    if (tk.t === 'id') {
      if (peek()?.v === '(') {
        next()
        const args = []
        if (peek()?.v !== ')') {
          for (;;) {
            // allow empty args like IF(A1,,1)
            if (peek()?.v === ',' || peek()?.v === ')') args.push({ k: 'lit', v: null })
            else args.push(expr(0))
            if (peek()?.v === ',') { next(); continue }
            break
          }
        }
        expect(')')
        return { k: 'call', name: tk.v, args }
      }
      if (tk.v === 'TRUE') return { k: 'lit', v: true }
      if (tk.v === 'FALSE') return { k: 'lit', v: false }
      return { k: 'lit', v: ERR.NAME }
    }
    if (tk.v === '(') {
      const e = expr(0)
      expect(')')
      return e
    }
    if (tk.v === '-' || tk.v === '+') return { k: 'unary', op: tk.v, e: expr(6) }
    throw ERR.PARSE
  }

  function expr(minPrec) {
    let left = primary()
    for (;;) {
      const tk = peek()
      if (!tk || tk.t !== 'op') break
      if (tk.v === '%') { next(); left = { k: 'pct', e: left }; continue }
      const prec = BINARY[tk.v]
      if (!prec || prec < minPrec) break
      next()
      // left-associative operators bind the right side tighter; ^ is right-associative
      const right = expr(tk.v === '^' ? prec : prec + 1)
      left = { k: 'bin', op: tk.v, a: left, b: right }
    }
    return left
  }

  const ast = expr(0)
  if (p !== tokens.length) throw ERR.PARSE
  return ast
}

// ---------------------------------------------------------------- coercion
function toNumber(v) {
  if (isError(v)) throw v
  if (v === null || v === undefined || v === '') return 0
  if (typeof v === 'number') return v
  if (typeof v === 'boolean') return v ? 1 : 0
  if (isIsoDate(v)) return dateToSerial(v)
  const lit = literalValue(String(v))
  if (typeof lit === 'number') return lit
  throw ERR.VALUE
}
function toText(v) {
  if (isError(v)) throw v
  if (v === null || v === undefined) return ''
  if (typeof v === 'boolean') return v ? 'TRUE' : 'FALSE'
  if (typeof v === 'number') return formatGeneral(v)
  return String(v)
}
function toBool(v) {
  if (isError(v)) throw v
  if (typeof v === 'boolean') return v
  if (v === null || v === '') return false
  if (typeof v === 'number') return v !== 0
  const up = String(v).toUpperCase()
  if (up === 'TRUE') return true
  if (up === 'FALSE') return false
  throw ERR.VALUE
}
function flatten(args) {
  const out = []
  for (const a of args) {
    if (Array.isArray(a)) for (const row of a) for (const v of row) out.push(v)
    else out.push(a)
  }
  return out
}
function numbersIn(args, strictScalars = true) {
  const out = []
  for (const a of args) {
    if (Array.isArray(a)) {
      for (const row of a) for (const v of row) {
        if (isError(v)) throw v
        if (typeof v === 'number') out.push(v)
      }
    } else if (a !== null && a !== undefined) {
      out.push(strictScalars ? toNumber(a) : a)
    }
  }
  return out
}
function compare(a, b) {
  if (isError(a)) throw a
  if (isError(b)) throw b
  // dates vs numbers compare as serial days; ISO dates vs ISO dates compare lexically (same result)
  if (typeof a === 'number' && isIsoDate(b)) b = dateToSerial(b)
  if (typeof b === 'number' && isIsoDate(a)) a = dateToSerial(a)
  if (a === null) a = typeof b === 'number' ? 0 : typeof b === 'boolean' ? false : ''
  if (b === null) b = typeof a === 'number' ? 0 : typeof a === 'boolean' ? false : ''
  const rank = (v) => (typeof v === 'number' ? 0 : typeof v === 'string' ? 1 : 2)
  if (rank(a) !== rank(b)) return rank(a) - rank(b)
  if (typeof a === 'string') {
    const x = a.toLowerCase(), y = b.toLowerCase()
    return x < y ? -1 : x > y ? 1 : 0
  }
  return a < b ? -1 : a > b ? 1 : 0
}
const round = (n, d) => {
  const f = Math.pow(10, d)
  return Math.round((n + Math.sign(n) * Number.EPSILON) * f) / f
}

// criteria for SUMIF / COUNTIF: ">5", "<>x", "=abc", "a*", 10
function makeCriteria(crit) {
  if (typeof crit === 'number' || typeof crit === 'boolean') return (v) => compare(v, crit) === 0 && v !== null
  const s = toText(crit)
  const m = /^(<=|>=|<>|<|>|=)?(.*)$/.exec(s)
  const op = m[1] || '='
  const rhsRaw = m[2]
  const rhs = literalValue(rhsRaw)
  const wildcard = typeof rhs === 'string' && /[*?]/.test(rhs)
  const re = wildcard ? new RegExp('^' + rhs.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$', 'i') : null
  return (v) => {
    if (isError(v)) return false
    if (op === '=' && rhsRaw === '') return v === null || v === ''
    if (op === '<>' && rhsRaw === '') return !(v === null || v === '')
    if (re) {
      const ok = v !== null && re.test(toText(v))
      return op === '<>' ? !ok : ok
    }
    if (v === null) return op === '<>'
    if ((typeof rhs === 'number') !== (typeof v === 'number') && !(isIsoDate(v) && typeof rhs === 'number') &&
        !(isIsoDate(rhs) && typeof v === 'number') && op !== '<>') {
      if (!(isIsoDate(v) && isIsoDate(rhs))) return false
    }
    const c = compare(v, rhs)
    switch (op) {
      case '=': return c === 0
      case '<>': return c !== 0
      case '<': return c < 0
      case '>': return c > 0
      case '<=': return c <= 0
      default: return c >= 0
    }
  }
}

// ---------------------------------------------------------------- functions
// Each function receives evaluated args (ranges are 2-D arrays). Lazy functions get AST + ctx.
const LAZY = {
  IF(args, ev) {
    if (args.length < 2) throw ERR.VALUE
    return toBool(ev(args[0])) ? ev(args[1]) : (args.length > 2 ? ev(args[2]) : false)
  },
  IFERROR(args, ev) {
    try {
      const v = ev(args[0])
      if (isError(v)) return ev(args[1])
      return v
    } catch (e) {
      if (isError(e)) return ev(args[1])
      throw e
    }
  },
  IFNA(args, ev) {
    try {
      const v = ev(args[0])
      return v === ERR.NA ? ev(args[1]) : v
    } catch (e) {
      if (e === ERR.NA) return ev(args[1])
      throw e
    }
  },
  ISERROR(args, ev) {
    try { return isError(ev(args[0])) } catch (e) { if (isError(e)) return true; throw e }
  }
}

const FUNCS = {
  SUM: (a) => numbersIn(a).reduce((s, n) => s + n, 0),
  AVERAGE: (a) => { const n = numbersIn(a); if (!n.length) throw ERR.DIV0; return n.reduce((s, x) => s + x, 0) / n.length },
  MIN: (a) => { const n = numbersIn(a); return n.length ? Math.min(...n) : 0 },
  MAX: (a) => { const n = numbersIn(a); return n.length ? Math.max(...n) : 0 },
  COUNT: (a) => flatten(a).filter((v) => typeof v === 'number').length,
  COUNTA: (a) => flatten(a).filter((v) => v !== null && v !== '').length,
  COUNTBLANK: (a) => flatten(a).filter((v) => v === null || v === '').length,
  PRODUCT: (a) => numbersIn(a).reduce((s, n) => s * n, 1),
  AND: (a) => flatten(a).filter((v) => v !== null).every(toBool),
  OR: (a) => flatten(a).filter((v) => v !== null).some(toBool),
  NOT: ([v]) => !toBool(v),
  ROUND: ([v, d]) => round(toNumber(v), toNumber(d ?? 0)),
  ROUNDUP: ([v, d]) => { const f = Math.pow(10, toNumber(d ?? 0)); const n = toNumber(v); return Math.sign(n) * Math.ceil(Math.abs(n) * f - 1e-9) / f },
  ROUNDDOWN: ([v, d]) => { const f = Math.pow(10, toNumber(d ?? 0)); const n = toNumber(v); return Math.sign(n) * Math.floor(Math.abs(n) * f + 1e-9) / f },
  CEILING: ([v, s]) => { const st = toNumber(s ?? 1); return st === 0 ? 0 : Math.ceil(toNumber(v) / st) * st },
  FLOOR: ([v, s]) => { const st = toNumber(s ?? 1); if (st === 0) throw ERR.DIV0; return Math.floor(toNumber(v) / st) * st },
  ABS: ([v]) => Math.abs(toNumber(v)),
  INT: ([v]) => Math.floor(toNumber(v)),
  MOD: ([a, b]) => { const d = toNumber(b); if (d === 0) throw ERR.DIV0; const n = toNumber(a); return n - d * Math.floor(n / d) },
  POWER: ([a, b]) => Math.pow(toNumber(a), toNumber(b)),
  SQRT: ([v]) => { const n = toNumber(v); if (n < 0) throw ERR.NUM; return Math.sqrt(n) },
  CONCAT: (a) => flatten(a).map(toText).join(''),
  CONCATENATE: (a) => flatten(a).map(toText).join(''),
  TEXTJOIN: ([sep, ignoreEmpty, ...rest]) => flatten(rest).map(toText).filter((s) => !toBool(ignoreEmpty) || s !== '').join(toText(sep)),
  LEN: ([v]) => toText(v).length,
  UPPER: ([v]) => toText(v).toUpperCase(),
  LOWER: ([v]) => toText(v).toLowerCase(),
  TRIM: ([v]) => toText(v).trim().replace(/\s+/g, ' '),
  LEFT: ([v, n]) => toText(v).slice(0, n === undefined ? 1 : toNumber(n)),
  RIGHT: ([v, n]) => { const s = toText(v); const k = n === undefined ? 1 : toNumber(n); return k <= 0 ? '' : s.slice(-k) },
  MID: ([v, start, n]) => toText(v).substr(toNumber(start) - 1, toNumber(n)),
  SUBSTITUTE: ([v, from, to]) => toText(v).split(toText(from)).join(toText(to)),
  FIND: ([needle, hay]) => { const i = toText(hay).indexOf(toText(needle)); if (i < 0) throw ERR.VALUE; return i + 1 },
  VALUE: ([v]) => toNumber(v),
  TEXT: ([v, fmt]) => formatNumber(toNumber(v), toText(fmt)),
  ISBLANK: ([v]) => v === null || v === '',
  ISNUMBER: ([v]) => typeof v === 'number',
  ISTEXT: ([v]) => typeof v === 'string',
  TODAY: () => todayIso(),
  NOW: () => new Date().toISOString().slice(0, 16).replace('T', ' '),
  DATE: ([y, m, d]) => serialToDate((Date.UTC(toNumber(y), toNumber(m) - 1, toNumber(d)) - EPOCH) / DAY_MS),
  YEAR: ([v]) => parseInt(serialToDate(toNumber(v)).slice(0, 4), 10),
  MONTH: ([v]) => parseInt(serialToDate(toNumber(v)).slice(5, 7), 10),
  DAY: ([v]) => parseInt(serialToDate(toNumber(v)).slice(8, 10), 10),
  DAYS: ([end, start]) => toNumber(end) - toNumber(start),
  WEEKDAY: ([v]) => new Date(EPOCH + toNumber(v) * DAY_MS).getUTCDay() + 1,
  NA: () => { throw ERR.NA },
  SUMIF: ([range, crit, sumRange]) => {
    const test = makeCriteria(crit)
    const src = asGrid(range), tgt = sumRange === undefined ? src : asGrid(sumRange)
    let s = 0
    src.forEach((row, i) => row.forEach((v, j) => { if (test(v)) { const t = tgt[i]?.[j]; if (typeof t === 'number') s += t } }))
    return s
  },
  COUNTIF: ([range, crit]) => {
    const test = makeCriteria(crit)
    return flatten([asGrid(range)]).filter(test).length
  },
  AVERAGEIF: ([range, crit, avgRange]) => {
    const test = makeCriteria(crit)
    const src = asGrid(range), tgt = avgRange === undefined ? src : asGrid(avgRange)
    const vals = []
    src.forEach((row, i) => row.forEach((v, j) => { if (test(v)) { const t = tgt[i]?.[j]; if (typeof t === 'number') vals.push(t) } }))
    if (!vals.length) throw ERR.DIV0
    return vals.reduce((a, b) => a + b, 0) / vals.length
  },
  SUMIFS: ([sumRange, ...pairs]) => {
    const tgt = asGrid(sumRange)
    const tests = []
    for (let i = 0; i < pairs.length; i += 2) tests.push([asGrid(pairs[i]), makeCriteria(pairs[i + 1])])
    let s = 0
    tgt.forEach((row, i) => row.forEach((v, j) => {
      if (typeof v === 'number' && tests.every(([g, t]) => t(g[i]?.[j] ?? null))) s += v
    }))
    return s
  },
  COUNTIFS: (pairs) => {
    const tests = []
    for (let i = 0; i < pairs.length; i += 2) tests.push([asGrid(pairs[i]), makeCriteria(pairs[i + 1])])
    if (!tests.length) return 0
    let n = 0
    tests[0][0].forEach((row, i) => row.forEach((_, j) => { if (tests.every(([g, t]) => t(g[i]?.[j] ?? null))) n++ }))
    return n
  },
  SUMPRODUCT: (arrays) => {
    const grids = arrays.map(asGrid)
    let s = 0
    grids[0].forEach((row, i) => row.forEach((_, j) => {
      let p = 1
      for (const g of grids) { const v = g[i]?.[j]; p *= typeof v === 'number' ? v : 0 }
      s += p
    }))
    return s
  },
  VLOOKUP: ([key, table, colNo, approx]) => {
    const g = asGrid(table)
    const col = toNumber(colNo) - 1
    if (col < 0 || (g[0] && col >= g[0].length)) throw ERR.REF
    const exact = approx !== undefined && approx !== null && !toBool(approx)
    let hit = -1
    for (let i = 0; i < g.length; i++) {
      const c = compare(g[i][0], key)
      if (c === 0 && g[i][0] !== null) { hit = i; break }
      if (!exact && c < 0) hit = i
      if (!exact && c > 0) break
    }
    if (hit < 0) throw ERR.NA
    return g[hit][col]
  },
  HLOOKUP: ([key, table, rowNo, approx]) => {
    const g = asGrid(table)
    const transposed = g[0] ? g[0].map((_, j) => g.map((row) => row[j])) : []
    return FUNCS.VLOOKUP([key, transposed, rowNo, approx])
  },
  MATCH: ([key, range, type]) => {
    const list = flatten([asGrid(range)])
    const t = type === undefined ? 1 : toNumber(type)
    if (t === 0) {
      const test = makeCriteria(typeof key === 'string' ? key : key)
      const i = list.findIndex((v) => (typeof key === 'string' ? test(v) : compare(v, key) === 0))
      if (i < 0) throw ERR.NA
      return i + 1
    }
    let hit = -1
    for (let i = 0; i < list.length; i++) {
      const c = compare(list[i], key)
      if (t > 0 ? c <= 0 : c >= 0) hit = i
      else break
    }
    if (hit < 0) throw ERR.NA
    return hit + 1
  },
  INDEX: ([range, row, col]) => {
    const g = asGrid(range)
    let r = toNumber(row ?? 1) - 1
    let c = col === undefined ? 0 : toNumber(col) - 1
    if (g.length === 1 && col === undefined) { c = r; r = 0 }
    if (r < 0 || c < 0 || r >= g.length || c >= (g[0]?.length ?? 0)) throw ERR.REF
    return g[r][c]
  }
}
const asGrid = (v) => (Array.isArray(v) ? v : [[v]])
export const FUNCTION_NAMES = [...Object.keys(FUNCS), ...Object.keys(LAZY)].sort()

// ---------------------------------------------------------------- evaluation
/**
 * Creates an evaluator over a sheet.
 * @param getRaw (r, c) => raw string|null
 * @returns {{ value(r,c): any, invalidate(): void }}
 */
export function createEvaluator(getRaw) {
  const cache = new Map()
  const astCache = new Map()
  const inProgress = new Set()

  function ast(raw) {
    let a = astCache.get(raw)
    if (a === undefined) {
      try { a = parse(raw.slice(1)) } catch (e) { a = { k: 'lit', v: isError(e) ? e : ERR.PARSE } }
      astCache.set(raw, a)
    }
    return a
  }

  function value(r, c) {
    const key = r * 100000 + c
    if (cache.has(key)) return cache.get(key)
    const raw = getRaw(r, c)
    let v
    if (raw === null || raw === undefined || raw === '') v = null
    else if (raw[0] !== '=' || raw.length === 1) v = literalValue(raw)
    else {
      if (inProgress.has(key)) return ERR.CYCLE
      inProgress.add(key)
      try {
        v = evalNode(ast(raw))
        if (Array.isArray(v)) v = v[0]?.[0] ?? null // implicit intersection: top-left of a range
        if (typeof v === 'number' && !Number.isFinite(v)) v = ERR.NUM
      } catch (e) {
        if (isError(e)) v = e
        else { console.error(e); v = ERR.VALUE }
      } finally {
        inProgress.delete(key)
      }
    }
    cache.set(key, v)
    return v
  }

  function evalNode(n) {
    switch (n.k) {
      case 'lit': return n.v
      case 'ref': return value(n.r, n.c)
      case 'range': {
        const out = []
        for (let r = n.r1; r <= n.r2; r++) {
          const row = []
          for (let c = n.c1; c <= n.c2; c++) row.push(value(r, c))
          out.push(row)
        }
        return out
      }
      case 'unary': {
        const v = toNumber(scalar(evalNode(n.e)))
        return n.op === '-' ? -v : v
      }
      case 'pct': return toNumber(scalar(evalNode(n.e))) / 100
      case 'bin': return binary(n.op, scalar(evalNode(n.a)), scalar(evalNode(n.b)))
      case 'call': {
        const lazy = LAZY[n.name]
        if (lazy) return lazy(n.args, (a) => scalarOrRange(evalNode(a)))
        const fn = FUNCS[n.name]
        if (!fn) throw ERR.NAME
        const args = n.args.map((a) => evalNode(a))
        return fn(args)
      }
      default: throw ERR.PARSE
    }
  }
  const scalar = (v) => {
    if (Array.isArray(v)) return v[0]?.[0] ?? null
    if (isError(v)) throw v
    return v
  }
  const scalarOrRange = (v) => v

  function binary(op, a, b) {
    switch (op) {
      case '+':
      case '-': {
        // date ± days stays a date; date − date is a number of days
        const aDate = isIsoDate(a), bDate = isIsoDate(b)
        const r = op === '+' ? toNumber(a) + toNumber(b) : toNumber(a) - toNumber(b)
        if (aDate && !bDate && typeof b !== 'string') return serialToDate(r)
        if (op === '+' && bDate && !aDate && typeof a !== 'string') return serialToDate(r)
        return r
      }
      case '*': return toNumber(a) * toNumber(b)
      case '/': { const d = toNumber(b); if (d === 0) throw ERR.DIV0; return toNumber(a) / d }
      case '^': return Math.pow(toNumber(a), toNumber(b))
      case '&': return toText(a) + toText(b)
      case '=': return compare(a, b) === 0
      case '<>': return compare(a, b) !== 0
      case '<': return compare(a, b) < 0
      case '>': return compare(a, b) > 0
      case '<=': return compare(a, b) <= 0
      case '>=': return compare(a, b) >= 0
      default: throw ERR.PARSE
    }
  }

  return {
    value,
    invalidate() { cache.clear() }
  }
}

// ---------------------------------------------------------------- formatting
export function formatGeneral(n) {
  if (Number.isInteger(n)) return String(n)
  const abs = Math.abs(n)
  if (abs !== 0 && (abs >= 1e15 || abs < 1e-9)) return n.toExponential(6).replace(/\.?0+e/, 'e')
  return String(parseFloat(n.toPrecision(12)))
}

/** Supports 0, 0.00, #,##0, #,##0.00, 0%, 0.0% style formats. */
export function formatNumber(n, fmt) {
  if (!fmt) return formatGeneral(n)
  const pct = fmt.endsWith('%')
  const core = pct ? fmt.slice(0, -1) : fmt
  const decimals = core.includes('.') ? core.split('.')[1].length : 0
  const grouping = core.includes(',')
  const v = pct ? n * 100 : n
  const s = v.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals, useGrouping: grouping })
  return pct ? s + '%' : s
}

export function displayValue(v, fmt) {
  if (v === null || v === undefined) return ''
  if (isError(v)) return v.code
  if (typeof v === 'boolean') return v ? 'TRUE' : 'FALSE'
  if (typeof v === 'number') return formatNumber(v, fmt)
  return String(v)
}

// ---------------------------------------------------------------- relative copy/paste
/** Shifts relative (non-$) references when a formula is pasted dr rows / dc cols away (Excel semantics). */
export function shiftFormula(raw, dr, dc) {
  if (!raw || raw[0] !== '=' || (dr === 0 && dc === 0)) return raw
  let out = ''
  let inStr = false
  let chunk = ''
  const flush = () => {
    out += chunk.replace(/(?<![A-Za-z0-9_.!])(\$?)([A-Za-z]{1,3})(\$?)(\d+)(?![A-Za-z0-9_(])/g, (m, ca, col, ra, row) => {
      const c = ca ? colIndex(col) : colIndex(col) + dc
      const r = ra ? parseInt(row, 10) - 1 : parseInt(row, 10) - 1 + dr
      if (c < 0 || r < 0) return '#REF!'
      return ca + colName(c) + ra + (r + 1)
    })
    chunk = ''
  }
  for (const ch of raw) {
    if (ch === '"') {
      if (!inStr) { flush(); inStr = true } else inStr = false
      out += ch
    } else if (inStr) out += ch
    else chunk += ch
  }
  flush()
  return out
}
