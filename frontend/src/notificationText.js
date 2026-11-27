import { locale } from './i18n.js'

/** Reactive reads let already loaded notifications and open toasts follow language changes. */
export function notificationText(notification) {
  return notification.translations?.[locale.value] ?? {
    title: notification.title ?? '',
    message: notification.message ?? ''
  }
}

export function changeRowLabel(change) {
  return change.rowLabelTranslations?.[locale.value] ?? change.rowLabel ?? ''
}
