# PowerShell Workflow

## Команда для запуска проекта в новом окне

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_10''; .\build\install\ai_advent_day_10\bin\ai_advent_day_10.bat'
```

## Что делает эта команда

Эта команда открывает новое окно PowerShell.

После открытия окна она:

1. переходит в папку проекта `C:\Users\compadre\Downloads\Projects\AiAdvent\day_10`;
2. запускает собранный bat-файл проекта:

```powershell
.\build\install\ai_advent_day_10\bin\ai_advent_day_10.bat
```

Ключ `-NoExit` оставляет окно PowerShell открытым после запуска, чтобы можно было работать с интерактивным приложением в этом окне.

## Правила для Codex

В этом проекте нужно различать отдельные команды пользователя для запуска основного приложения и запуска сравнения стратегий.

### Команда: `собери проект`

Если пользователь пишет `собери проект`, по умолчанию это означает один обязательный атомарный workflow:

1. очистить runtime-файлы истории в `config/conversations/`;
2. выполнить сборку:

```powershell
.\gradlew.bat build
.\gradlew.bat installDist
```

3. после этого открыть новое окно PowerShell и запустить проект командой:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_10''; .\build\install\ai_advent_day_10\bin\ai_advent_day_10.bat'
```

То есть `собери проект` в этом репозитории означает:
`очистка runtime-истории -> build -> installDist -> запуск проекта в новом окне PowerShell`.

Правило очистки истории:
- нужно удалять только runtime-файлы истории моделей в `config/conversations/`;
- нельзя удалять всю папку `config/conversations/` целиком.

Дополнительное обязательное правило:
- команда `собери проект` не считается выполненной, если был сделан только `build` или только `installDist`, но не был выполнен запуск;
- после очистки истории, `build` и `installDist` нужно автоматически доводить задачу до запуска, а не останавливаться и не спрашивать отдельно, если пользователь явно не просил только сборку без запуска;
- если один из промежуточных шагов завершился неуспешно, нужно сообщить, что workflow прерван на этом шаге и запуск не был выполнен.

Короткий чеклист для `собери проект`:
1. удалить runtime-файлы истории в `config/conversations/`
2. `.\gradlew.bat build`
3. `.\gradlew.bat installDist`
4. `Start-Process ... ai_advent_day_10.bat`

### Команда: `запусти проект`

Если пользователь пишет `запусти проект`, по умолчанию это означает только запуск уже собранной версии без предварительной сборки.

Исключение:

Если видно, что сборка давно не выполнялась, артефакты отсутствуют или есть основания считать текущую сборку несвежей, команду `запусти проект` нужно трактовать как `собери проект`.

Нужно сразу выполнить:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_10''; .\build\install\ai_advent_day_10\bin\ai_advent_day_10.bat'
```

То есть `запусти проект` в этом репозитории означает:
`только запуск в новом окне PowerShell без build и без installDist`.

### Дополнение

Если пользователь не уточняет и просит открыть проект, открыть терминал с проектом или формулирует похожую просьбу, по умолчанию нужно трактовать это ближе к команде `запусти проект`, то есть как запуск уже собранной версии в отдельном окне PowerShell.

### Команда: `собери сравнение`

Если пользователь пишет `собери сравнение`, по умолчанию это означает один обязательный атомарный workflow для полного прогона comparison runner:

1. очистить старые артефакты сравнения:
   - удалить файлы в `build/reports/strategy-comparison/`, если они есть;
   - удалить файлы состояний в `build/strategy-comparison/state/`, если они есть;
2. выполнить проверку проекта:

```powershell
.\gradlew.bat test
```

3. после этого запустить сравнение стратегий:

```powershell
.\gradlew.bat compareStrategies
```

То есть `собери сравнение` в этом репозитории означает:
`очистка старых comparison-артефактов -> test -> compareStrategies`.

По умолчанию `compareStrategies` запускается:
- в укороченном режиме на `5` шагах сценария;
- с включённым LLM judge.

Дополнительное обязательное правило:
- команда `собери сравнение` не считается выполненной, если были сделаны только `test` или только `compareStrategies`, но не был выполнен весь workflow;
- после очистки артефактов нужно автоматически доводить задачу до запуска сравнения, а не останавливаться на промежуточных шагах, если пользователь явно не просил только проверку или только запуск;
- если один из промежуточных шагов завершился неуспешно, нужно сообщить, что workflow сравнения прерван на этом шаге и итоговый запуск не был выполнен.

Короткий чеклист для `собери сравнение`:
1. удалить старые файлы в `build/reports/strategy-comparison/`
2. удалить старые файлы в `build/strategy-comparison/state/`
3. `.\gradlew.bat test`
4. `.\gradlew.bat compareStrategies`

Если пользователь явно указывает количество шагов, нужно запускать:

```powershell
.\gradlew.bat compareStrategies -PcomparisonSteps=<N>
```

### Команда: `запусти сравнение`

Если пользователь пишет `запусти сравнение`, по умолчанию это означает только запуск уже подготовленного comparison runner без предварительной очистки и без обязательного `test`.

Нужно сразу выполнить:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','chcp 65001 > $null; [Console]::InputEncoding = [System.Text.Encoding]::UTF8; [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; $OutputEncoding = [System.Text.Encoding]::UTF8; Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_10''; .\gradlew.bat compareStrategies'
```

То есть `запусти сравнение` в этом репозитории означает:
`только запуск compareStrategies в новом окне PowerShell`.

По умолчанию такой запуск:
- использует `5` шагов сценария;
- включает LLM judge.

Исключение:

Если видно, что после последних изменений сравнение не проверялось, comparison-артефакты сильно устарели или есть основания считать текущее состояние несвежим, команду `запусти сравнение` нужно трактовать как `собери сравнение`.

Дополнительное уточнение:

Если пользователь явно просит конкретное количество шагов, быстрый или полный прогон сравнения, нужно использовать параметр:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','chcp 65001 > $null; [Console]::InputEncoding = [System.Text.Encoding]::UTF8; [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; $OutputEncoding = [System.Text.Encoding]::UTF8; Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_10''; .\gradlew.bat compareStrategies -PcomparisonSteps=<N>'
```

где `<N>` — число шагов сценария, которое пользователь попросил.

Практическая трактовка:
- если пользователь просто пишет `запусти сравнение`, запускать сравнение на `5` шагах с включённым judge;
- если пользователь пишет `запусти сравнение на 8 шагах` или аналогично, использовать `-PcomparisonSteps=8`;
- если пользователь явно просит выключить judge, использовать `-PcomparisonJudge=false`;
- если пользователь явно просит полный прогон, запускать без ограничения только после отдельного подтверждения, потому что это заметно дольше и дороже по токенам.
