import { describe, expect, it } from 'vitest'
import { createEvaluator, displayValue, shiftFormula, literalValue, formatNumber, isError } from './engine.js'

function sheet(cells) {
  // cells: { A1: '1', B2: '=A1*2' }
  const map = new Map()
  for (const [ref, raw] of Object.entries(cells)) {
    const m = /^([A-Z]+)(\d+)$/.exec(ref)
    const c = m[1].split('').reduce((n, ch) => n * 26 + ch.charCodeAt(0) - 64, 0) - 1
    map.set(`${parseInt(m[2], 10) - 1},${c}`, raw)
  }
  const ev = createEvaluator((r, c) => map.get(`${r},${c}`) ?? null)
  return (ref) => {
    const m = /^([A-Z]+)(\d+)$/.exec(ref)
    const c = m[1].split('').reduce((n, ch) => n * 26 + ch.charCodeAt(0) - 64, 0) - 1
    return ev.value(parseInt(m[2], 10) - 1, c)
  }
}

describe('arithmetic & precedence', () => {
  it('follows Excel precedence', () => {
    const v = sheet({ A1: '=1+2*3', A2: '=(1+2)*3', A3: '=2^3^2', A4: '=-2^2', A5: '=10-4-3', A6: '=50%', A7: '="a"&1+1' })
    expect(v('A1')).toBe(7)
    expect(v('A2')).toBe(9)
    expect(v('A3')).toBe(512)
    expect(v('A4')).toBe(4)
    expect(v('A5')).toBe(3)
    expect(v('A6')).toBe(0.5)
    expect(v('A7')).toBe('a2')
  })

  it('references, ranges and blanks', () => {
    const v = sheet({ A1: '10', A2: '20', A3: '', B1: '=A1+A2+A3', B2: '=SUM(A1:A3)', B3: '=AVERAGE(A1:A2)', B4: '=A9*2' })
    expect(v('B1')).toBe(30)
    expect(v('B2')).toBe(30)
    expect(v('B3')).toBe(15)
    expect(v('B4')).toBe(0)
  })

  it('errors propagate and cycles are detected', () => {
    const v = sheet({ A1: '=1/0', A2: '=A1+1', A3: '=A4', A4: '=A3', A5: '=FOO(1)', A6: '=1+', A7: '=IFERROR(A1,"x")' })
    expect(displayValue(v('A1'))).toBe('#DIV/0!')
    expect(displayValue(v('A2'))).toBe('#DIV/0!')
    expect(displayValue(v('A3'))).toBe('#CYCLE!')
    expect(displayValue(v('A5'))).toBe('#NAME?')
    expect(isError(v('A6'))).toBe(true)
    expect(v('A7')).toBe('x')
  })
})

describe('functions', () => {
  it('inventory-style formulas', () => {
    const v = sheet({ E2: '1200', F2: '800', G2: '=E2-F2', H2: '500', I2: '=MAX(0,H2-G2)', E3: '90', F3: '400', G3: '=E3-F3', H3: '300', I3: '=MAX(0,H3-G3)' })
    expect(v('I2')).toBe(100)
    expect(v('I3')).toBe(610)
  })

  it('IF / AND / OR / comparisons', () => {
    const v = sheet({ A1: '5', B1: '=IF(A1>3,"big","small")', B2: '=AND(A1>1,A1<10)', B3: '=OR(A1=1,A1=2)', B4: '=IF(A1<>5,1,)' })
    expect(v('B1')).toBe('big')
    expect(v('B2')).toBe(true)
    expect(v('B3')).toBe(false)
    expect(v('B4')).toBe(null)
  })

  it('lookups and conditional aggregates', () => {
    const v = sheet({
      A1: 'M-1', B1: '10', A2: 'M-2', B2: '20', A3: 'M-1', B3: '5',
      D1: '=VLOOKUP("M-2",A1:B3,2,FALSE)', D2: '=SUMIF(A1:A3,"M-1",B1:B3)', D3: '=COUNTIF(B1:B3,">=10")',
      D4: '=INDEX(B1:B3,MATCH("M-2",A1:A3,0))', D5: '=VLOOKUP("X",A1:B3,2,FALSE)', D6: '=COUNTIF(A1:A3,"M*")',
      D7: '=SUMPRODUCT(B1:B3,B1:B3)'
    })
    expect(v('D1')).toBe(20)
    expect(v('D2')).toBe(15)
    expect(v('D3')).toBe(2)
    expect(v('D4')).toBe(20)
    expect(displayValue(v('D5'))).toBe('#N/A')
    expect(v('D6')).toBe(3)
    expect(v('D7')).toBe(525)
  })

  it('text and rounding', () => {
    const v = sheet({ A1: ' hello  world ', B1: '=TRIM(A1)', B2: '=UPPER(LEFT(B1,5))', B3: '=ROUND(2.345,2)', B4: '=ROUND(1.85*400,2)', B5: '=LEN("abc")' })
    expect(v('B1')).toBe('hello world')
    expect(v('B2')).toBe('HELLO')
    expect(v('B3')).toBe(2.35)
    expect(v('B4')).toBe(740)
    expect(v('B5')).toBe(3)
  })

  it('dates behave like Excel serials but display as ISO', () => {
    const v = sheet({ A1: '2026-11-15', A2: '2026-11-20', B1: '=A2-A1', B2: '=A1+7', B3: '=A2>A1', B4: '=DAYS(A2,A1)', B5: '=DATE(2026,12,31)', B6: '=IF(A2>A1,"LATE","OK")' })
    expect(v('B1')).toBe(5)
    expect(v('B2')).toBe('2026-11-22')
    expect(v('B3')).toBe(true)
    expect(v('B4')).toBe(5)
    expect(v('B5')).toBe('2026-12-31')
    expect(v('B6')).toBe('LATE')
  })
})

describe('helpers', () => {
  it('parses literals', () => {
    expect(literalValue('42')).toBe(42)
    expect(literalValue('1,200.5')).toBe(1200.5)
    expect(literalValue('15%')).toBe(0.15)
    expect(literalValue('TRUE')).toBe(true)
    expect(literalValue('M-1001')).toBe('M-1001')
    expect(literalValue('007')).toBe(7)
  })

  it('formats numbers', () => {
    expect(formatNumber(1234.5, '#,##0.00')).toBe('1,234.50')
    expect(formatNumber(0.256, '0.0%')).toBe('25.6%')
    expect(formatNumber(0.1 + 0.2)).toBe('0.3')
  })

  it('shifts relative references on paste', () => {
    expect(shiftFormula('=E2-F2', 1, 0)).toBe('=E3-F3')
    expect(shiftFormula('=$A$1+A1+"A1"', 2, 1)).toBe('=$A$1+B3+"A1"')
    expect(shiftFormula('=SUM(A1:A3)', 0, 2)).toBe('=SUM(C1:C3)')
    expect(shiftFormula('=A1', -1, 0)).toBe('=#REF!')
  })
})
