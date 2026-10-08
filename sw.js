// MẠNG TRƯỚC — CACHE DỰ PHÒNG:
// Có mạng: luôn tải bản MỚI NHẤT, đồng thời lưu một bản dự phòng.
// Mất mạng: mở app bằng bản dự phòng đã lưu lần gần nhất.
const CACHE = 'btgght-offline-v32';

self.addEventListener('install', () => { self.skipWaiting(); });

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return; // đồng bộ Firestore đi thẳng, không đụng
  let nenLuu = false;
  try {
    const u = new URL(e.request.url);
    nenLuu = u.origin === self.location.origin || u.hostname.endsWith('gstatic.com');
  } catch (err) {}
  e.respondWith(
    fetch(e.request, { cache: 'no-store' })
      .then(res => {
        if (nenLuu && res && res.ok) {
          const clone = res.clone();
          caches.open(CACHE).then(c => c.put(e.request, clone)).catch(() => {});
        }
        return res;
      })
      .catch(() => caches.match(e.request).then(m => m || Response.error()))
  );
});
