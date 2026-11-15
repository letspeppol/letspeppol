import { createElement, type CSSProperties } from 'react';
import { addons, types, useGlobals, useStorybookApi, useStorybookState } from 'storybook/manager-api';

const ISSUES_URL = 'https://github.com/letspeppol/letspeppol/issues';
const TITLE_PREFIX = 'Design feedback';

const linkStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  height: 28,
  padding: '0 12px',
  border: '1px solid #2C3C69',
  borderRadius: 6,
  fontSize: 13,
  fontWeight: 600,
  textDecoration: 'none',
  whiteSpace: 'nowrap',
};

function newIssueUrl(page: string, theme: string, locale: string): string {
  const body = [
    `Page: ${page}`,
    `Link: ${window.location.href}`,
    `Theme: ${theme}, language: ${locale}`,
    '',
    '### What I see',
    '',
    '',
    '### What I expect',
    '',
  ].join('\n');
  return `${ISSUES_URL}/new?${new URLSearchParams({ title: `${TITLE_PREFIX}: ${page}: `, body })}`;
}

function openIssuesUrl(page: string): string {
  return `${ISSUES_URL}?${new URLSearchParams({ q: `is:issue is:open in:title "${TITLE_PREFIX}: ${page}"` })}`;
}

function FeedbackLinks() {
  const api = useStorybookApi();
  useStorybookState();
  const [globals] = useGlobals();
  const page = api.getCurrentStoryData()?.title ?? 'General';

  return createElement(
    'div',
    { style: { display: 'flex', alignItems: 'center', gap: 8, marginRight: 8 } },
    createElement(
      'a',
      { href: openIssuesUrl(page), target: '_blank', rel: 'noreferrer', style: { ...linkStyle, color: '#2C3C69' } },
      'Feedback on this page',
    ),
    createElement(
      'a',
      {
        href: newIssueUrl(page, globals.theme ?? 'light', globals.locale ?? 'en'),
        target: '_blank',
        rel: 'noreferrer',
        style: { ...linkStyle, background: '#2C3C69', color: '#FFFFFF' },
      },
      'Give feedback',
    ),
  );
}

addons.register('letspeppol/feedback', () => {
  addons.add('letspeppol/feedback/links', {
    type: types.TOOLEXTRA,
    title: 'Feedback',
    match: ({ viewMode }) => viewMode === 'docs' || viewMode === 'story',
    render: FeedbackLinks,
  });
});
