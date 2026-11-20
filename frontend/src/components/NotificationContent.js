import { defineComponent, h } from 'vue'
import { notificationText } from '../notificationText.js'

/** A mounted component keeps persistent IMPORTANT toasts reactive to language changes. */
export default defineComponent({
  props: { notification: { type: Object, required: true } },
  setup(props) {
    return () => {
      const n = props.notification
      const text = notificationText(n)
      return h('div', [
        h('div', { style: { fontSize: '16px', fontWeight: '700', marginBottom: '6px', color: 'var(--text-1)' } }, text.title),
        h('div', text.message.split('\n').slice(0, 3).join('\n')),
        h('div', `— ${n.actorName ?? ''} (${n.actorDepartment ?? ''})`)
      ])
    }
  }
})
