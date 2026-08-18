# AGENTS.md

## Branches

Toujours créer une branche dédiée pour chaque changement (ex. `fix/resume-expired-streams`, `feat/next-marks-watched`) et ne jamais commiter directement sur `main`.

## Commits

Les commits doivent respecter la spec [Conventional Commits](https://www.conventionalcommits.org/fr/v1.0.0/) et être préfixés par un [Gitmoji](https://gitmoji.dev) pertinent.

Format :

```
<gitmoji> <type>(scope optionnel): <description>
```

Exemples :

```text
✨ feat(player): marque la vidéo comme lue sur le bouton suivant
🐛 fix(player): corrige la reprise de lecture sur les streams expirés
📝 docs: documente le format des commits
🚀 chore(deps): met à jour les dépendances
```

- Le type suit Conventional Commits : `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- L'emoji doit refléter le type (✨ pour `feat`, 🐛 pour `fix`, 📝 pour `docs`, etc.).
- La description doit être courte, à l'impératif, sans majuscule initiale ni point final.