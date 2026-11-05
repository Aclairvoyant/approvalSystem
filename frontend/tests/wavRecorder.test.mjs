import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/utils/wavRecorder.ts', import.meta.url), 'utf8')

function loadRecorder(options = {}) {
  const {
    secure = true,
    hasMediaDevices = true,
    hasAudioContext = true,
    protocol = 'https:',
    hostname = 'example.com',
  } = options

  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }).outputText

  const exports = {}
  const module = { exports }
  class FakeAudioContext {}

  const sandbox = {
    exports,
    module,
    window: {
      isSecureContext: secure,
      AudioContext: hasAudioContext ? FakeAudioContext : undefined,
      webkitAudioContext: undefined,
    },
    navigator: {
      mediaDevices: hasMediaDevices
        ? { getUserMedia: () => Promise.resolve({ getTracks: () => [] }) }
        : undefined,
    },
    location: { protocol, hostname },
    Blob,
    ArrayBuffer,
    DataView,
    Float32Array,
    Math,
  }

  vm.createContext(sandbox)
  vm.runInContext(compiled, sandbox, { filename: 'wavRecorder.ts' })
  return module.exports
}

const secureRecorder = loadRecorder()
assert.equal(typeof secureRecorder.getRecordingSupportStatus, 'function')
assert.equal(secureRecorder.isRecordingSupported(), true)
assert.equal(secureRecorder.getRecordingSupportStatus().supported, true)

const insecureRecorder = loadRecorder({ secure: false, protocol: 'http:', hostname: '192.168.1.8' })
assert.equal(insecureRecorder.isRecordingSupported(), false)
assert.equal(insecureRecorder.getRecordingSupportStatus().reason, 'insecure-context')
assert.match(insecureRecorder.getRecordingSupportStatus().message, /HTTPS|localhost/)

const missingMediaRecorder = loadRecorder({ hasMediaDevices: false })
assert.equal(missingMediaRecorder.isRecordingSupported(), false)
assert.equal(missingMediaRecorder.getRecordingSupportStatus().reason, 'missing-media-devices')

const missingAudioRecorder = loadRecorder({ hasAudioContext: false })
assert.equal(missingAudioRecorder.isRecordingSupported(), false)
assert.equal(missingAudioRecorder.getRecordingSupportStatus().reason, 'missing-audio-context')

console.log('wavRecorder support detection tests passed')
