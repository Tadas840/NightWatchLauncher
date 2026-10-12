# NightWatch launcher

Viskas, ką gali keisti, ir kaip išleisti atnaujinimą žaidėjams.

## Kas kur keičiama

| Ką keisti | Kur |
|---|---|
| Serverio IP, Minecraft / Fabric versija, modai iš interneto, API adresas | `nightwatch/nightwatch.json` |
| Žaidimo meniu (tekstai, spalvos, mygtukai, logotipas, fonas) | `nightwatch/client/client.json` (+ paveikslėliai tame pačiame aplanke) |
| Savi modai be interneto nuorodos | įmesk `.jar` į `nightwatch/mods/` |
| Launcherio tekstai, nuorodos (Discord, YouTube...) | `app/assets/lang/_custom.toml` |
| Launcherio vertimas | `app/assets/lang/lt_LT.toml` |
| Launcherio fonas | `app/assets/images/backgrounds/0.jpg` |
| Launcherio išvaizda | `app/assets/css/launcher.css` |
| NightWatch modo kodas | `client-mod/` |
| Serverio pluginas (statistika, prisijungimas, naujienos) | `server-plugin/` |

Serveryje (`Server Purpur/plugins/NightWatchAPI/`):

| Ką keisti | Kur |
|---|---|
| Statistikos lentelė, prisijungimo nustatymai | `config.yml` |
| Naujienos | `news.yml` |

Pakeitus serverio failus: `/nightwatchapi reload`.

## Po pakeitimų launcheryje

```
npm run nightwatch                 # sukompiliuoja modą ir sugeneruoja distribution.json
npm run nightwatch:distribution    # jei keitei tik nustatymus / modų sąrašą
npm start                          # išbandyti
```

## Atnaujinimo išleidimas žaidėjams

Vieną kartą:

1. Sukurk **public** GitHub repo (pvz. `NightWatchLauncher`).
2. `electron-builder.yml` → `publish.owner` – GitHub vardas, `publish.repo` – repo pavadinimas (dabar `Tadas840/NightWatchLauncher`).
3. Tą patį įrašyk `package.json` → `repository.url` ir `dev-app-update.yml`.
4. `git remote set-url origin https://github.com/<vardas>/<repo>.git`
5. `git push -u origin master`

Kiekvienam atnaujinimui:

1. `npm run nightwatch` (jei keitei modą ar nustatymus) ir `git commit` pakeitimus.
2. `npm run release`
   - padidina versiją (pvz. 1.0.0 → 1.0.1), sukuria tag'ą `v1.0.1` ir išsiunčia į GitHub;
   - GitHub Actions sukompiliuoja `NightWatch Launcher-setup-1.0.1.exe` ir paskelbia release.
3. Žaidėjų launcheriai per ~30 min. (arba iš karto paleidus) atsisiunčia atnaujinimą ir pasiūlo persikrauti.

Pirmą kartą žaidėjams duok `.exe` iš GitHub Releases puslapio.
