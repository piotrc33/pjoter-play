// Renders the download button and the app list from apps.json, so a release only has to update the catalog.

// Keep in sync with the manager's applicationId and its entry in apps.json.
const MANAGER_ID = 'pl.pjoterplay.app';

// API level -> the version name people know from their phone's settings.
const ANDROID_VERSIONS = {
  26: '8.0', 27: '8.1', 28: '9', 29: '10', 30: '11', 31: '12', 32: '12L',
  33: '13', 34: '14', 35: '15', 36: '16', 37: '17',
};

function androidName(minSdk) {
  return `Android ${ANDROID_VERSIONS[minSdk] ?? `API ${minSdk}`} lub nowszy`;
}

function megabytes(bytes) {
  return `${(bytes / 1_000_000).toLocaleString('pl-PL', { maximumFractionDigits: 1 })} MB`;
}

function showManager(manager) {
  const meta = document.getElementById('download-meta');
  meta.textContent = androidName(manager.minSdk);
  if (!manager.release) return;

  const button = document.getElementById('download');
  button.href = manager.release.apk;
  button.removeAttribute('aria-disabled');
  button.textContent = 'Pobierz Pjoter Play';
  meta.textContent =
    `Wersja ${manager.release.versionName} · ${megabytes(manager.release.size)} · ${androidName(manager.minSdk)}`;
}

function appCard(app) {
  const item = document.createElement('li');
  item.className = 'app';

  const icon = document.createElement('img');
  icon.className = 'icon';
  icon.src = app.icon;
  icon.alt = '';
  icon.width = icon.height = 56;

  const body = document.createElement('div');
  const name = document.createElement('h3');
  name.textContent = app.name;
  const summary = document.createElement('p');
  summary.className = 'summary';
  summary.textContent = app.summary;
  const description = document.createElement('p');
  description.textContent = app.description;
  const meta = document.createElement('p');
  meta.className = 'meta';
  const version = app.release
    ? `Wersja ${app.release.versionName} · ${megabytes(app.release.size)}`
    : 'W przygotowaniu';
  meta.textContent = `${version} · ${androidName(app.minSdk)}`;

  body.append(name, summary, description, meta);
  item.append(icon, body);
  return item;
}

async function main() {
  try {
    // no-cache: a fresh release should show up on the next visit, not after the browser's cache expires.
    const response = await fetch('apps.json', { cache: 'no-cache' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const { apps } = await response.json();

    const manager = apps.find((app) => app.id === MANAGER_ID);
    if (manager) showManager(manager);
    document.getElementById('apps').append(
      ...apps.filter((app) => app.id !== MANAGER_ID).map(appCard),
    );
  } catch (error) {
    console.error(error);
    document.getElementById('apps-error').hidden = false;
  }
}

main();
