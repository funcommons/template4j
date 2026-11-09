// Filesystem helpers for screenshots + reports.

import { writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

export function ensureDir(dir) {
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
}

export function screenshotPath(dir, name) {
  return resolve(dir, `${name}.png`)
}

export function writeReport(dir, name, payload) {
  writeFileSync(resolve(dir, `${name}.json`), JSON.stringify(payload, null, 2))
}
