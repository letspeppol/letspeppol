const svg = (paths: string, size = 18) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${paths}</svg>`;

export const icon = {
  plus: (size?: number) => svg('<path d="M12 5v14M5 12h14"/>', size),
  send: (size?: number) => svg('<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>', size),
  trash: (size?: number) => svg('<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M3 6h18"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>', size),
  pencil: (size?: number) => svg('<path d="M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z"/>', size),
  download: (size?: number) => svg('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/>', size),
  check: (size?: number) => svg('<path d="M20 6 9 17l-5-5"/>', size),
  close: (size?: number) => svg('<path d="M18 6 6 18"/><path d="m6 6 12 12"/>', size),
  search: (size?: number) => svg('<path d="m21 21-4.35-4.35"/><circle cx="11" cy="11" r="7"/>', size),
  info: (size?: number) => svg('<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>', size),
  warning: (size?: number) => svg('<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>', size),
  danger: (size?: number) => svg('<circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/>', size),
  ok: (size?: number) => svg('<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/>', size),
  file: (size?: number) => svg('<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/>', size),
  users: (size?: number) => svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>', size),
  inbox: (size?: number) => svg('<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>', size),
};
