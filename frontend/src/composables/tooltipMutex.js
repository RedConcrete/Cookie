let _globalClose = null

export function registerGlobal(fn) {
  if (_globalClose && _globalClose !== fn) _globalClose()
  _globalClose = fn
}

export function unregisterGlobal(fn) {
  if (_globalClose === fn) _globalClose = null
}
