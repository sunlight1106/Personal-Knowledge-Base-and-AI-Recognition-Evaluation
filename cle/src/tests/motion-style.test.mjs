import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { parse, compileStyle } from '@vue/compiler-sfc'
import postcss from 'postcss'
import { JSDOM } from 'jsdom'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const scope = 'data-v-motion-regression'
const rulesFor = relative => {
  const filename = resolve(root, relative)
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename })
  const rules = []
  for (const style of descriptor.styles) {
    const compiled = compileStyle({ source: style.content, filename, id: scope, scoped: style.scoped })
    assert.deepEqual(compiled.errors, [])
    postcss.parse(compiled.code).walkRules(rule => {
      if (rule.selector.includes('data-motion')) rules.push(rule)
    })
  }
  return rules
}
const fixture = () => new JSDOM(`<html data-motion="off"><head></head><body><div id="app">
  <div class="ambient-waves" ${scope}><svg ${scope}>
    <g class="water-rest" ${scope}></g><g class="wave-flow" ${scope}></g>
    <g class="water-rings" ${scope}><ellipse ${scope}></ellipse></g>
    <path class="water-drop" ${scope}></path><g class="water-splash" ${scope}></g>
    <ellipse class="water-impact" ${scope}></ellipse><ellipse class="water-light" ${scope}></ellipse>
  </svg></div>
  <div class="preview-page" ${scope}></div>
  <div class="provider-card" ${scope}><a href="#">Choose</a></div>
  <div class="notification-popup-enter-active" ${scope}></div>
</div></body></html>`)
const matchesDeclaration = (rules, element, property, value) => rules.some(rule =>
  element.matches(rule.selector) && rule.nodes.some(node => node.prop === property && (!value || node.value === value)))

test('compiled motion selectors never target page roots in either preference state', () => {
  const files = readdirSync(root, { recursive: true }).filter(file => file.endsWith('.vue'))
  const { window } = fixture()
  try {
    const roots = [window.document.documentElement, window.document.body, window.document.getElementById('app')]
    for (const file of files) for (const rule of rulesFor(file)) for (const state of ['off', 'on']) {
      roots[0].dataset.motion = state
      for (const element of roots) assert.equal(element.matches(rule.selector), false, `${file}: ${rule.selector} affects ${element.tagName}`)
    }
  } finally { window.close() }
})

test('paused decoration keeps the entire document opaque and only dims the resting ripples', () => {
  const { window } = fixture()
  try {
    const style = window.document.createElement('style')
    style.textContent = 'html,body,#app{opacity:1}' + rulesFor('components/AmbientWaves.vue').map(rule => rule.toString()).join('\n')
    window.document.head.append(style)
    for (const selector of ['html', 'body', '#app']) assert.equal(window.getComputedStyle(window.document.querySelector(selector)).opacity, '1', selector)
    assert.equal(window.getComputedStyle(window.document.querySelector('.water-rest')).opacity, '0.2')
    for (const selector of ['.wave-flow', '.water-rings ellipse', '.water-drop', '.water-splash', '.water-impact', '.water-light']) {
      assert.equal(window.getComputedStyle(window.document.querySelector(selector)).animation, 'none', selector)
    }
  } finally { window.close() }
})

test('preview, provider and notification motion rules still reach their intended controls', () => {
  const { window } = fixture()
  const document = window.document
  try {
    assert.ok(matchesDeclaration(rulesFor('components/NotificationCenter.vue'), document.querySelector('.notification-popup-enter-active'), 'transition', 'none'))
    document.documentElement.dataset.motion = 'on'
    assert.ok(matchesDeclaration(rulesFor('components/HomeWorkspacePreview.vue'), document.querySelector('.preview-page'), 'animation'))
    assert.ok(matchesDeclaration(rulesFor('views/ModelsView.vue'), document.querySelector('.provider-card'), 'transition'))
    assert.equal(matchesDeclaration(rulesFor('components/AmbientWaves.vue'), document.querySelector('.water-rest'), 'opacity'), false)
    const waves = rulesFor('components/AmbientWaves.vue')
    for (const selector of ['.wave-flow', '.water-drop', '.water-rings ellipse']) {
      assert.equal(matchesDeclaration(waves, document.querySelector(selector), 'animation', 'none'), false, `${selector} must respect explicit enable`)
    }
    assert.equal(matchesDeclaration(waves, document.querySelector('.water-rings'), 'display', 'none'), false)
  } finally { window.close() }
})
