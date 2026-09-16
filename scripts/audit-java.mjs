// OSV receives only public Maven package coordinates, never application data.
import { readFile, writeFile } from 'node:fs/promises'
const sbomPath = new URL('../target/sbom-java.json', import.meta.url)
const components = JSON.parse(await readFile(sbomPath, 'utf8')).components
const packages = [...new Map(components.map(component => [`${component.group}:${component.name}@${component.version}`, {
  package: { ecosystem: 'Maven', name: `${component.group}:${component.name}` }, version: component.version
}])).values()]

async function json(url, init) {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(30000) })
  if (!response.ok) throw new Error(`Advisory lookup failed: HTTP ${response.status}`)
  return response.json()
}

const findings = []
let queries = packages
while (queries.length) {
  const { results } = await json('https://api.osv.dev/v1/querybatch', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ queries })
  })
  if (!Array.isArray(results) || results.length !== queries.length) throw new Error('Incomplete advisory response')
  const next = []
  for (let i = 0; i < results.length; i++) {
    for (const vulnerability of results[i].vulns ?? []) findings.push({ ...queries[i], id: vulnerability.id })
    if (results[i].next_page_token) next.push({ ...queries[i], page_token: results[i].next_page_token })
  }
  queries = next
}
const advisories = {}
for (const id of new Set(findings.map(finding => finding.id))) advisories[id] = await json(`https://api.osv.dev/v1/vulns/${encodeURIComponent(id)}`)
const active = findings.filter(finding => !advisories[finding.id].withdrawn)
await writeFile(new URL('../target/osv-java-audit.json', import.meta.url), JSON.stringify({ checkedAt: new Date().toISOString(), packages, findings: active, advisories }, null, 2))
console.log(`OSV checked ${packages.length} Maven package versions; ${active.length} findings.`)
for (const finding of active) console.log(`${finding.package.name}@${finding.version}: ${finding.id} ${advisories[finding.id].summary}`)
if (active.length) process.exitCode = 1
