import { createApp, h, nextTick, ref, withDirectives, vModelSelect } from 'vue'
import SelectField from '../components/SelectField.vue'
import { setUiLanguage, uiText } from '../i18n'

const fixture = document.getElementById('fixture'), checks = []
const assert = (value, name) => { if (!value) throw Error(name); checks.push(name) }
const tick = async () => { await nextTick(); await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
const key = async (target, value, extra = {}) => { const event = new window.KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true, ...extra }); target.dispatchEvent(event); await tick(); return event }
const pointer = target => target.dispatchEvent(new Event('pointerdown', { bubbles: true }))
const menu = () => document.querySelector('.select-menu')
const trigger = () => fixture.querySelector('button[role=combobox]')
const options = () => [...document.querySelectorAll('.select-menu [role=option]')]
const active = () => document.querySelector('.select-menu .is-active')
let app, changes = 0, submitted = 0
const value = ref('a'), locked = ref(false), items = ref([
  { value: 'a', text: 'Alpha' }, { value: 'b', text: 'Beta', disabled: true }, { value: 'c', text: 'Charlie' },
])
function control(extra = {}) {
  return h(SelectField, null, { default: () => withDirectives(h('select', {
    name: 'choice', class: 'field-input', disabled: locked.value, ...extra,
    'onUpdate:modelValue': newValue => { value.value = newValue }, onChange: () => { changes++ },
  }, items.value.map(item => h('option', { value: item.value, disabled: item.disabled }, item.text))), [[vModelSelect, value.value]]) })
}
async function mount(render) { app?.unmount(); fixture.innerHTML = ''; app = createApp({ render }); app.mount(fixture); await tick() }
try {
  await mount(() => h('form', { onSubmit: e => { e.preventDefault(); submitted++ } }, [h('label', ['Practice mode', control()]), h('button', { type: 'button', id: 'after' }, 'Next')]))
  assert(trigger().textContent.includes('Alpha'), 'Selected native value appears in the custom trigger')
  assert(trigger().getAttribute('aria-label') === 'Practice mode', 'Wrapped labels name the visible combobox without including all option text')
  assert(fixture.querySelector('select').getAttribute('aria-hidden') === 'true' && fixture.querySelector('select').tabIndex === -1, 'The backing native control is absent from the accessibility and tab order')
  assert(new window.FormData(fixture.querySelector('form')).get('choice') === 'a', 'Native form serialization remains intact')
  trigger().click(); await tick()
  assert(menu() && options().length === 3 && trigger().getAttribute('aria-expanded') === 'true', 'Click opens the styled listbox')
  assert(options()[0].getAttribute('aria-selected') === 'true' && options()[0].querySelector('svg'), 'Current option has a semantic selected state and check mark')
  assert(!menu().querySelector('input'), 'Short lists avoid an unnecessary search field')
  assert(document.getElementById(trigger().getAttribute('aria-activedescendant')) === active(), 'Keyboard focus is linked to an existing active option')
  assert(menu().parentElement === document.body, 'Dropdown escapes clipping containers')
  await key(trigger(), 'ArrowDown')
  assert(active().textContent === 'Charlie' && value.value === 'a', 'Arrow navigation skips disabled options without changing data')
  await key(trigger(), 'Enter')
  assert(value.value === 'c' && changes === 1 && !menu(), 'Enter commits exactly one native change')
  assert(document.activeElement === trigger() && submitted === 0, 'Selection restores focus without submitting a surrounding form')
  trigger().click(); await tick(); await key(trigger(), 'Home'); await key(trigger(), 'Escape')
  assert(value.value === 'c' && changes === 1 && !menu(), 'Escape cancels navigation and retains the previous selection')
  trigger().click(); await tick(); options()[1].click(); await tick()
  assert(value.value === 'c' && menu(), 'Disabled rows cannot be committed with a mouse')
  options()[2].click(); await tick(); assert(changes === 1 && !menu(), 'Choosing the current option does not issue another change')
  await key(trigger(), 'a'); assert(active().textContent === 'Alpha', 'Typeahead finds a short-list option')
  pointer(document.body); await tick(); assert(!menu() && value.value === 'c', 'Outside click dismisses without changing the selection')
  trigger().click(); await tick(); await key(trigger(), 'Tab')
  assert(!menu() && document.activeElement === trigger(), 'Tab closes the menu from the trigger so normal document focus order can continue')
  value.value = 'a'; await tick(); assert(trigger().textContent.includes('Alpha'), 'Programmatic Vue model updates refresh the displayed value')
  fixture.querySelector('select').value = 'c'; fixture.querySelector('select').dispatchEvent(new Event('change', { bubbles: true })); await tick()
  assert(value.value === 'c' && trigger().textContent.includes('Charlie'), 'Existing native change integrations remain compatible')
  trigger().click(); await tick(); locked.value = true; await tick()
  assert(!menu() && trigger().disabled, 'Becoming disabled while open closes the menu')
  trigger().click(); await tick(); assert(!menu(), 'Disabled controls cannot open')
  locked.value = false; await tick()
  items.value = [{ value: 'a', text: 'Alpha updated' }, { value: 'd', text: 'Delta' }]; value.value = 'd'; await tick()
  assert(trigger().textContent.includes('Delta'), 'Dynamic options and selected models update together')
  trigger().click(); await tick(); assert(options().length === 2 && options()[0].textContent.includes('updated'), 'Updated option labels are not cached stale')
  window.dispatchEvent(new Event('resize')); await tick(); assert(!menu(), 'Resize closes a potentially stale positioned menu')
  items.value = Array.from({ length: 30 }, (_, i) => ({ value: `word-${i}`, text: `Word ${i}` })); value.value = 'word-18'; await tick()
  trigger().click(); await tick()
  const input = menu().querySelector('input')
  assert(input && document.activeElement === input && options().length === 30, 'Long lists provide an immediately focused search field')
  input.value = 'Word 2'; input.dispatchEvent(new Event('input', { bubbles: true })); await tick()
  assert(options().length === 12 && options().every(item => item.textContent.includes('2')), 'Search filters all choices by terms')
  input.value = 'missing'; input.dispatchEvent(new Event('input', { bubbles: true })); await tick()
  assert(options().length === 0 && menu().querySelector('[role=status]') && !input.hasAttribute('aria-activedescendant'), 'No-results state has no dangling active-option reference')
  await key(input, 'Enter'); assert(menu() && value.value === 'word-18', 'Enter with no results does not clear existing data')
  input.value = 'Word 27'; input.dispatchEvent(new Event('input', { bubbles: true })); await tick()
  await key(input, 'Enter', { isComposing: true }); assert(menu() && value.value === 'word-18', 'IME composition Enter does not accidentally commit')
  await key(input, 'Enter'); assert(value.value === 'word-27' && !menu(), 'Filtered selection retains its original native option index')
  trigger().click(); await tick(); assert(menu().querySelector('input').value === '', 'Search is cleared when reopening')
  document.querySelector('.select-menu__list').dispatchEvent(new Event('scroll')); await tick(); assert(menu(), 'Scrolling inside options does not dismiss the menu')
  window.dispatchEvent(new Event('scroll')); await tick(); assert(!menu(), 'Scrolling the page closes a detached popup')
  await setUiLanguage('en'); trigger().click(); await tick()
  assert(menu().querySelector('input').placeholder === 'Search options…' && menu().textContent.includes('Enter Select'), 'Search and keyboard hints follow interface language')
  app.unmount(); app = null; await tick(); assert(!menu(), 'Unmount removes the teleported menu and listeners')
  await setUiLanguage('zh')
  items.value = [{ value: false, text: 'No' }, { value: true, text: 'Yes' }]; value.value = false
  await mount(() => h('label', ['Boolean', control()]))
  trigger().click(); await tick(); options()[1].click(); await tick()
  assert(value.value === true, 'Vue boolean option identity is preserved through native change')
  items.value = [{ value: 7, text: '7 days' }, { value: 30, text: '30 days' }]; value.value = 7; await tick()
  trigger().click(); await tick(); options()[1].click(); await tick()
  assert(value.value === 30 && typeof value.value === 'number', 'Numeric option identity is preserved')
  items.value = [{ value: '', text: 'Pick one' }, { value: 'a', text: 'Alpha' }]; value.value = ''
  await mount(() => h('form', [h('label', { for: 'required-choice' }, 'Required choice'), control({ id: 'required-choice', required: true })]))
  fixture.querySelector('form').checkValidity(); await tick()
  assert(trigger().getAttribute('aria-invalid') === 'true' && document.activeElement === trigger(), 'Required validation focuses the visible control rather than a hidden native select')
  assert(fixture.querySelector('[role=alert]') && trigger().getAttribute('aria-label') === 'Required choice', 'Validation feedback and explicit label associations remain available')
  trigger().click(); await tick(); options()[1].click(); await tick()
  assert(!trigger().hasAttribute('aria-invalid') && fixture.querySelector('form').checkValidity(), 'A valid selection clears validation feedback')
  await mount(() => h('dialog', { open: true }, [h('label', ['Modal choice', control()]), h('button', { type: 'button' }, 'Close')]))
  trigger().click(); await tick()
  assert(menu().parentElement === fixture.querySelector('dialog'), 'Dialog dropdowns remain inside the native top layer')
  const escaped = await key(trigger(), 'Escape')
  assert(escaped.defaultPrevented && fixture.querySelector('dialog').open && !menu(), 'Escape consumes only the dropdown, leaving its parent dialog open')
  await mount(() => h('div', [h('label', ['First', control()]), h('label', ['Second', control()])]))
  const buttons = fixture.querySelectorAll('button[role=combobox]')
  buttons[0].click(); await tick(); buttons[1].click(); await tick()
  assert(document.querySelectorAll('.select-menu').length === 1 && buttons[0].getAttribute('aria-expanded') === 'false', 'Only one select menu can remain open')
  app.unmount(); app = null; await tick()
  assert(!menu(), 'All popup nodes are removed after the final unmount')
  document.getElementById('results').textContent = `PASS ${checks.length}\n${checks.join('\n')}`; document.documentElement.dataset.result = 'passed'
} catch (error) {
  document.getElementById('results').textContent = `FAIL ${checks.length}: ${error.stack}`; document.documentElement.dataset.result = 'failed'
} finally { app?.unmount(); await setUiLanguage('zh') }
