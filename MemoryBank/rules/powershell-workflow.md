# PowerShell Workflow

## Команда для запуска проекта в новом окне

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_9''; .\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat'
```

## Что делает эта команда

Эта команда открывает новое окно PowerShell.

После открытия окна она:

1. переходит в папку проекта `C:\Users\compadre\Downloads\Projects\AiAdvent\day_9`;
2. запускает собранный bat-файл проекта:

```powershell
.\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat
```

Ключ `-NoExit` оставляет окно PowerShell открытым после запуска, чтобы можно было работать с интерактивным приложением в этом окне.

## Правила для Codex

В этом проекте нужно различать две отдельные команды пользователя.

### Команда: `собери проект`

Если пользователь пишет `собери проект`, по умолчанию это означает один обязательный атомарный workflow:

1. очистить runtime-файлы истории в `config/conversations/`, но не удалять пресеты;
2. выполнить сборку:

```powershell
.\gradlew.bat build
.\gradlew.bat installDist
```

3. после этого открыть новое окно PowerShell и запустить проект командой:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_9''; .\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat'
```

То есть `собери проект` в этом репозитории означает:
`очистка runtime-истории -> build -> installDist -> запуск проекта в новом окне PowerShell`.

Правило очистки истории:
- нужно удалять только runtime-файлы истории моделей в `config/conversations/`;
- нельзя удалять `context_overflow_preset.json`;
- нельзя удалять всю папку `config/conversations/` целиком.

Дополнительное обязательное правило:
- команда `собери проект` не считается выполненной, если был сделан только `build` или только `installDist`, но не был выполнен запуск;
- после очистки истории, `build` и `installDist` нужно автоматически доводить задачу до запуска, а не останавливаться и не спрашивать отдельно, если пользователь явно не просил только сборку без запуска;
- если один из промежуточных шагов завершился неуспешно, нужно сообщить, что workflow прерван на этом шаге и запуск не был выполнен.

Короткий чеклист для `собери проект`:
1. удалить runtime-файлы истории в `config/conversations/`, кроме `context_overflow_preset.json`
2. `.\gradlew.bat build`
3. `.\gradlew.bat installDist`
4. `Start-Process ... ai_advent_day_9.bat`

### Команда: `запусти проект`

Если пользователь пишет `запусти проект`, по умолчанию это означает только запуск уже собранной версии без предварительной сборки.

Исключение:

Если видно, что сборка давно не выполнялась, артефакты отсутствуют или есть основания считать текущую сборку несвежей, команду `запусти проект` нужно трактовать как `собери проект`.

Нужно сразу выполнить:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_9''; .\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat'
```

То есть `запусти проект` в этом репозитории означает:
`только запуск в новом окне PowerShell без build и без installDist`.

### Дополнение

Если пользователь не уточняет и просит открыть проект, открыть терминал с проектом или формулирует похожую просьбу, по умолчанию нужно трактовать это ближе к команде `запусти проект`, то есть как запуск уже собранной версии в отдельном окне PowerShell.
