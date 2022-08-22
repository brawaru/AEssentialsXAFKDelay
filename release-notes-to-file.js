// @ts-check

const fs = require('node:fs/promises');
const path = require('node:path');

/**
 * @typedef {object} Config
 * @prop {string?} path Path to file.
 */

/**
 * @typedef {object} NextRelease
 * @prop {string} notes Release notes.
 */

/**
 * @typedef {object} Context
 * @prop {NextRelease} nextRelease The next release.
 */

/**
 * 
 * @param {Config} config Config for the plugin.
 * @param {Context} context Context.
 */
async function prepare(config, context) {
  let { path: changelogPath } = config || {};
  if (changelogPath == null) {
    changelogPath = './$CHANGELOG.md';
  }
  await fs.writeFile(path.resolve(process.cwd(), changelogPath), context.nextRelease.notes, { encoding: 'utf8' });
}

module.exports = { prepare };