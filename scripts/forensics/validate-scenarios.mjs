import { readFile, readdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import process from 'node:process';

const root = resolve(process.cwd(), 'sandbox', 'scenarios');
const allowedFaults = new Set([
  'TIMEOUT',
  'DELAY',
  'DUPLICATE',
  'FAIL_BEFORE_COMMIT',
  'FAIL_AFTER_COMMIT',
  'KAFKA_UNAVAILABLE',
]);

const files = (await readdir(root))
  .filter(name => name.endsWith('.scenario.json'))
  .sort();
const errors = [];

for (const file of files) {
  let scenario;
  try {
    scenario = JSON.parse(await readFile(resolve(root, file), 'utf8'));
  } catch (error) {
    errors.push(`${file}: invalid JSON (${error.message})`);
    continue;
  }
  validateEnvelope(file, scenario, errors);
}

if (errors.length) {
  errors.forEach(error => process.stderr.write(`${error}\n`));
  process.exit(1);
}

process.stdout.write(`Validated ${files.length} confirmed forensic scenario artifact(s).\n`);

function validateEnvelope(file, scenario, issues) {
  const requiredText = ['scenarioId', 'sourceIncidentId', 'sourceEvidenceRef', 'confirmedBy', 'confirmedAt'];
  for (const field of requiredText) {
    if (typeof scenario[field] !== 'string' || scenario[field].trim() === '') {
      issues.push(`${file}: ${field} is required`);
    }
  }
  if (scenario.sanitized !== true || scenario.status !== 'CONFIRMED') {
    issues.push(`${file}: only sanitized CONFIRMED scenarios may enter the repository`);
  }
  if (!scenario.definition || typeof scenario.definition !== 'object' || Array.isArray(scenario.definition)) {
    issues.push(`${file}: definition must be an object`);
    return;
  }
  validateDefinition(file, scenario.definition, issues);
}

function validateDefinition(file, definition, issues) {
  if (definition.schemaVersion !== 1) {
    issues.push(`${file}: definition.schemaVersion must equal 1`);
  }
  if (!Array.isArray(definition.faults) || definition.faults.length > 20) {
    issues.push(`${file}: definition.faults must be an array with at most 20 entries`);
    return;
  }
  definition.faults.forEach((fault, index) => validateFault(file, fault, index, issues));
}

function validateFault(file, fault, index, issues) {
  const prefix = `${file}: definition.faults[${index}]`;
  if (!fault || typeof fault !== 'object' || Array.isArray(fault)) {
    issues.push(`${prefix} must be an object`);
    return;
  }
  if (!allowedFaults.has(fault.type)) issues.push(`${prefix}.type is not allowlisted`);
  if (typeof fault.target !== 'string' || fault.target.trim() === '' || fault.target.length > 120) {
    issues.push(`${prefix}.target must be 1..120 characters`);
  }
  integerRange(issues, prefix, fault, 'occurrence', 1, 1000, 1);
  integerRange(issues, prefix, fault, 'probabilityBps', 0, 10000, 10000);
  const delayMs = integerRange(issues, prefix, fault, 'delayMs', 0, 60000, 0);
  if (fault.type === 'DELAY' && delayMs === 0) issues.push(`${prefix}.delayMs must be greater than zero for DELAY`);
  if (fault.type !== 'DELAY' && delayMs !== 0) issues.push(`${prefix}.delayMs is only valid for DELAY`);
}

function integerRange(issues, prefix, object, field, minimum, maximum, fallback) {
  const value = object[field] ?? fallback;
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    issues.push(`${prefix}.${field} must be an integer from ${minimum} to ${maximum}`);
  }
  return value;
}
