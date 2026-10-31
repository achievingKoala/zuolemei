self.addEventListener('install', event => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', event => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const { taskId, url } = event.notification.data || {};
  if (!taskId || !url) return;
  const action = ['complete', 'snooze', 'snooze10'].includes(event.action) ? event.action : 'open';
  event.waitUntil((async () => {
    const target = new URL(url);
    if (target.origin !== self.location.origin) return;
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const page = windows.find(client => {
      const candidate = new URL(client.url);
      return candidate.origin === target.origin && candidate.pathname === target.pathname;
    });
    if (page) {
      page.postMessage({ type: 'notification-action', taskId, action });
      if (action === 'open') await page.focus();
    } else if (action === 'open') {
      target.searchParams.set('notificationTask', taskId);
      target.searchParams.set('notificationAction', action);
      await self.clients.openWindow(target.href);
    } else {
      const pending = await caches.open('bark-pending-actions-v1');
      const key = new URL('./__notification_action__/' + crypto.randomUUID(), self.location.href);
      await pending.put(key.href, new Response(JSON.stringify({ taskId, action, time: Date.now() })));
    }
  })());
});
