export interface NavItem {
  label: string
  to: string
  icon: string
  permission?: string
  any?: string[]
}

export const mainItems: NavItem[] = [
  { label: '学习中心', to: '/app/research', icon: 'book', permission: 'research:use' },
  { label: '工作台', to: '/app/home', icon: 'home', permission: 'dashboard:read' },
  { label: '知识库', to: '/app/knowledge', icon: 'book', permission: 'knowledge:read' },
  { label: '背单词', to: '/app/vocabulary', icon: 'book', permission: 'vocabulary:use' },
  { label: '我的笔记', to: '/app/notes', icon: 'note', permission: 'note:read' },
  { label: '实验台', to: '/app/upload', icon: 'spark', permission: 'experiment:run' },
  { label: '模型中心', to: '/app/models', icon: 'model', permission: 'model:read' },
  { label: '素材库', to: '/app/images', icon: 'images', any: ['file:read', 'file:read:any'] },
  { label: '调用日志', to: '/app/logs', icon: 'logs', any: ['experiment:read', 'experiment:read:any'] },
  { label: '优化对比', to: '/app/comparisons', icon: 'compare', any: ['experiment:read', 'experiment:read:any'] },
  { label: '下载中心', to: '/app/downloads', icon: 'download', any: ['file:read', 'file:read:any', 'experiment:read', 'experiment:read:any'] },
  { label: '余额与充值', to: '/app/billing', icon: 'wallet', permission: 'billing:read' },
  { label: '联系人与聊天', to: '/app/contacts', icon: 'users', permission: 'contacts:use' },
  { label: '站内信箱', to: '/app/mail', icon: 'mail', permission: 'message:read' },
  { label: '群组协作', to: '/app/groups', icon: 'users', permission: 'group:use' },
]

export const adminItems: NavItem[] = [
  { label: '举报审核', to: '/app/moderation', icon: 'shield', permission: 'moderation:review' },
  { label: '平台总览', to: '/app/admin', icon: 'logs', any: ['admin:stats', 'admin:audit'] },
  { label: '用户管理', to: '/app/users', icon: 'users', permission: 'user:read' },
  { label: '权限管理', to: '/app/roles', icon: 'shield', permission: 'role:read' },
]

export const supportItems: NavItem[] = [
  { label: '举报与处罚记录', to: '/app/safety', icon: 'shield' },
  { label: '操作文档', to: '/docs', icon: 'docs' },
  { label: '设置', to: '/app/settings', icon: 'settings' },
]

export const navigationItems = [...mainItems, ...adminItems, ...supportItems]
export function canVisit(item: NavItem, permissions: readonly string[] = []) {
  return item.permission ? permissions.includes(item.permission)
    : item.any ? item.any.some(permission => permissions.includes(permission)) : true
}
