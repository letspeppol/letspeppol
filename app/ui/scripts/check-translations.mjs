import console from "node:console";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const projectRoot = path.resolve(import.meta.dirname, "..");
const sourceRoot = path.join(projectRoot, "src");
const localeRoot = path.join(sourceRoot, "app", "locale");
const fix = process.argv.includes("--fix");
const collator = new Intl.Collator("en");

function walk(directory, extension) {
  return fs.readdirSync(directory, {withFileTypes: true}).flatMap(entry => {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(entryPath, extension);
    return entry.isFile() && entry.name.endsWith(extension) ? [entryPath] : [];
  });
}

function flattenTranslations(value, prefix = "", result = new Set()) {
  if (typeof value === "string") {
    result.add(prefix);
    return result;
  }

  if (!value || Array.isArray(value) || typeof value !== "object") {
    throw new Error(`Translation '${prefix}' must be a string or an object`);
  }

  for (const [key, child] of Object.entries(value)) {
    flattenTranslations(child, prefix ? `${prefix}.${key}` : key, result);
  }
  return result;
}

function sortedCopy(value) {
  if (!value || Array.isArray(value) || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.keys(value)
      .sort(collator.compare)
      .map(key => [key, sortedCopy(value[key])]),
  );
}

function findUnsortedObjects(value, prefix = "<root>", result = []) {
  if (!value || Array.isArray(value) || typeof value !== "object") return result;

  const actual = Object.keys(value);
  const sorted = [...actual].sort(collator.compare);
  if (actual.some((key, index) => key !== sorted[index])) result.push(prefix);

  for (const [key, child] of Object.entries(value)) {
    findUnsortedObjects(child, prefix === "<root>" ? key : `${prefix}.${key}`, result);
  }
  return result;
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function parseTranslationInstruction(instruction, isBound) {
  const withoutTarget = instruction.replace(/^\[[^\]]+\]/, "").trim();
  if (!withoutTarget) return [];

  // Aurelia expressions such as ternaries contain their literal keys in quotes.
  const quotedKeys = [...withoutTarget.matchAll(/["']([A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)+)["']/g)]
    .map(match => ({type: "exact", value: match[1]}));
  if (quotedKeys.length > 0) return quotedKeys;

  if (withoutTarget.includes("${")) {
    const pattern = withoutTarget
      .split(/\$\{[^}]+\}/)
      .map(escapeRegExp)
      .join("[^.]+?");
    return [{type: "pattern", value: withoutTarget, regex: new RegExp(`^${pattern}$`)}];
  }

  // A bound variable cannot be resolved statically.
  if (isBound && /^[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*$/.test(withoutTarget)) return [];

  return /^[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*$/.test(withoutTarget)
    ? [{type: "exact", value: withoutTarget}]
    : [];
}

function htmlTranslationReferences(file) {
  const source = fs.readFileSync(file, "utf8");
  const references = [];
  const attributePattern = /\bt(\.(?:bind|one-time))?\s*=\s*(["'])([\s\S]*?)\2/g;

  for (const match of source.matchAll(attributePattern)) {
    const line = source.slice(0, match.index).split(/\r?\n/).length;
    for (const instruction of match[3].split(";")) {
      for (const reference of parseTranslationInstruction(instruction, Boolean(match[1]))) {
        references.push({...reference, file, line});
      }
    }
  }
  return references;
}

function typescriptTranslationReferences(file) {
  const source = fs.readFileSync(file, "utf8");
  const references = [];
  const callPattern = /\.tr\(\s*(["'`])([\s\S]*?)\1/g;

  for (const match of source.matchAll(callPattern)) {
    const line = source.slice(0, match.index).split(/\r?\n/).length;
    for (const reference of parseTranslationInstruction(match[2], false)) {
      references.push({...reference, file, line});
    }
  }
  return references;
}

const localeFiles = fs.readdirSync(localeRoot)
  .filter(file => /^translation_[a-z-]+\.json$/i.test(file))
  .sort(collator.compare)
  .map(file => path.join(localeRoot, file));

if (localeFiles.length === 0) throw new Error(`No translation files found in ${localeRoot}`);

const errors = [];
const locales = localeFiles.map(file => {
  const source = fs.readFileSync(file, "utf8");
  const translations = JSON.parse(source);
  const unsorted = findUnsortedObjects(translations);

  if (fix) {
    fs.writeFileSync(file, `${JSON.stringify(sortedCopy(translations), null, 2)}\n`);
  } else if (unsorted.length > 0) {
    errors.push(`${path.relative(projectRoot, file)} has unsorted keys in: ${unsorted.join(", ")}`);
  }

  return {file, keys: flattenTranslations(translations)};
});

const allLocaleKeys = new Set(locales.flatMap(locale => [...locale.keys]));
for (const locale of locales) {
  const missing = [...allLocaleKeys].filter(key => !locale.keys.has(key)).sort(collator.compare);
  if (missing.length > 0) {
    errors.push(`${path.relative(projectRoot, locale.file)} is missing:\n  ${missing.join("\n  ")}`);
  }
}

const htmlFiles = walk(sourceRoot, ".html");
const typescriptFiles = walk(sourceRoot, ".ts");
const references = [
  ...htmlFiles.flatMap(htmlTranslationReferences),
  ...typescriptFiles.flatMap(typescriptTranslationReferences),
];
for (const reference of references) {
  const relativeLocation = `${path.relative(projectRoot, reference.file)}:${reference.line}`;
  for (const locale of locales) {
    const found = reference.type === "exact"
      ? locale.keys.has(reference.value)
      : [...locale.keys].some(key => reference.regex.test(key));
    if (!found) {
      errors.push(`${relativeLocation} references '${reference.value}', missing from ${path.basename(locale.file)}`);
    }
  }
}

if (errors.length > 0) {
  console.error(`Translation validation failed:\n\n${errors.join("\n")}`);
  process.exitCode = 1;
} else {
  const mode = fix ? "sorted" : "validated";
  console.log(`Translations ${mode}: ${locales.length} locales, ${allLocaleKeys.size} keys, ${htmlFiles.length} HTML files, ${typescriptFiles.length} TypeScript files.`);
}
