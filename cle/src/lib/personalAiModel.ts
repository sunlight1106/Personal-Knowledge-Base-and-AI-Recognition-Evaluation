export function modelFieldError(provider: string, value: string): string {
  const model = value.trim()
  if (provider === 'MOONSHOT' && /^ak-/i.test(model))
    return '这里需要模型名称，不是 API 密钥列表中的 ID。请查询下方模型目录并选择。'
  if (/^sk-/i.test(model))
    return '这里需要模型名称，API 密钥请填写在下方的密钥输入框。'
  return ''
}
