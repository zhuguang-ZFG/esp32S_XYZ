import { t } from '@/i18n'

/**
 * 任务状态格式化：D2 从 pages/index/index.vue 提取。
 *
 * 复用于首页的「最近任务」卡片（label/color/progress 三态一致）。
 */
export function useTaskFormatters() {
  function taskStatusLabel(status: string): string {
    const map: Record<string, string> = {
      pending: t('create.status.pending'),
      queued: t('create.status.queued'),
      created: t('create.status.pending'),
      dispatching: t('create.status.queued'),
      dispatched: t('create.status.queued'),
      accepted: t('create.status.queued'),
      running: t('create.status.running'),
      progress: t('create.status.running'),
      done: t('create.status.completed'),
      completed: t('create.status.completed'),
      failed: t('create.status.failed'),
      error: t('create.status.error'),
      cancelled: t('create.status.failed'),
      dead_letter: t('create.status.failed'),
    }
    return map[status] || status
  }

  function taskStatusColor(status: string): string {
    const map: Record<string, string> = {
      pending: 'var(--amber)',
      queued: 'var(--amber)',
      created: 'var(--amber)',
      dispatching: 'var(--amber)',
      dispatched: 'var(--amber)',
      accepted: 'var(--accent)',
      running: 'var(--accent)',
      progress: 'var(--accent)',
      done: 'var(--green)',
      completed: 'var(--green)',
      failed: 'var(--danger)',
      error: 'var(--danger)',
      cancelled: 'var(--muted)',
      dead_letter: 'var(--danger)',
    }
    return map[status] || 'var(--muted)'
  }

  function taskProgress(status: string): number {
    const map: Record<string, number> = {
      pending: 10,
      queued: 20,
      created: 10,
      dispatching: 25,
      dispatched: 30,
      accepted: 35,
      running: 60,
      progress: 70,
      done: 100,
      completed: 100,
      failed: 100,
      error: 100,
      cancelled: 100,
      dead_letter: 100,
    }
    return map[status] ?? 0
  }

  return { taskStatusLabel, taskStatusColor, taskProgress }
}
