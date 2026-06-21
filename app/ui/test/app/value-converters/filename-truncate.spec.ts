import { describe, it, expect } from 'vitest';
import { FilenameTruncateConverter } from '../../../src/app/value-converters/filename-truncate';

describe('FilenameTruncateConverter', () => {
  const converter = new FilenameTruncateConverter();
  const truncate = (name: string, max?: number) => converter.toView(name, max);

  it('returns short names unchanged', () => {
    expect(truncate('invoice.pdf')).toBe('invoice.pdf');
  });

  it('uses a compact default budget that keeps the extension and exactly one ellipsis', () => {
    const result = truncate('p_16_1208955305_263801375415_123704845_20260616.pdf');
    expect(result.length).toBeLessThanOrEqual(22); // default budget, fits the attachment card
    expect(result.endsWith('.pdf')).toBe(true);
    expect(result.includes('...')).toBe(true);
    expect(result.includes('....')).toBe(false); // never more than three dots
  });

  it('truncates a long name and preserves the extension (exact "stem... .ext" shape)', () => {
    expect(truncate('a very long invoice name.pdf', 20)).toBe('a very long... .pdf');
  });

  it('keeps the start of the name, the ellipsis and the extension, within budget', () => {
    const result = truncate('p_16_1208955305_263801375415_123704845_20260616.pdf', 30);
    expect(result.startsWith('p_16_')).toBe(true);
    expect(result).toContain('...');
    expect(result.endsWith('.pdf')).toBe(true);
    expect(result.length).toBeLessThanOrEqual(30);
  });

  it('still surfaces the extension when it barely fits the budget', () => {
    const result = truncate('longfilename.pdf', 8);
    expect(result.endsWith('.pdf')).toBe(true); // never silently dropped
    expect(result.length).toBeLessThanOrEqual(8);
  });

  it('shows the extension start (never an extension-less stub) when the extension is huge', () => {
    const result = truncate('name.superlongextension', 10);
    expect(result.startsWith('n')).toBe(true);
    expect(result).toContain('...');
    expect(result).toContain('.s'); // extension surfaced, not dropped
    expect(result.length).toBeLessThanOrEqual(10);
  });

  it('end-truncates names without a usable extension', () => {
    const result = truncate('a_name_without_any_extension_at_all', 20);
    expect(result.endsWith('...')).toBe(true);
    expect(result.length).toBeLessThanOrEqual(20);
  });

  it('treats leading-dot (hidden) and trailing-dot files as having no extension', () => {
    expect(truncate('.a_very_long_hidden_filename_here', 15).length).toBeLessThanOrEqual(15);
    expect(truncate('a_very_long_name_with_trailing_dot.', 15).endsWith('...')).toBe(true);
  });

  it('keeps only the last segment of a multi-part extension', () => {
    const result = truncate('this_is_a_really_long_archive_name.tar.gz', 30);
    expect(result.endsWith('.gz')).toBe(true);
    expect(result.length).toBeLessThanOrEqual(30);
  });

  it('never returns more characters than maxLength, even for tiny budgets', () => {
    expect(truncate('file.pdf', 1).length).toBeLessThanOrEqual(1);
    expect(truncate('file.pdf', 3).length).toBeLessThanOrEqual(3);
    expect(truncate('file.pdf', 0)).toBe('');
    expect(truncate('file.pdf', -5)).toBe('');
  });

  it('handles empty / null / undefined safely', () => {
    expect(truncate('')).toBe('');
    expect(converter.toView(undefined as unknown as string)).toBe('');
    expect(converter.toView(null as unknown as string)).toBe('');
  });
});
