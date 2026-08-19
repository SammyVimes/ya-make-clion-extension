# Ya Make for CLion

Плагин позволяет открыть корень Arcadia/YDB как обычный проект CLion и выбрать
нужный `ya.make` как текущую область индексации. Отдельный проект от
`ya ide vscode` не требуется.

## Работа с проектом

1. В Remote Development откройте `/home/senya/development/ydb`.
2. Откройте нужный `ya.make` и нажмите **Use target and refresh** в верхней панели редактора.
3. Плагин выполнит тот же C++ codegen, который записывает `ya ide vscode`, затем
   создаст `compile_commands.json` в project data-каталоге CLion, подставит
   компиляторы из `ya tool` и загрузит compilation database в уже открытый
   проект CLion. Корень репозитория при этом не изменяется.
4. Повторное обновление доступно из панели `ya.make` и через
   **Tools | Refresh Ya C++ Project**.

Сгенерированные исходники показываются в Project View отдельным корнем
`[codegen]`. Пути `ya`, выбранной цели и codegen можно изменить в
**Settings | Build, Execution, Deployment | Ya Make**.

## Запуск тестов

У `Y_UNIT_TEST` есть стандартный gutter Run/Debug. Конфигурация использует
нативный **Custom Build Application** CLion:

- Before Launch вызывает `ya test --test-prepare --keep-temps`;
- Run и Debug запускают подготовленный тестовый бинарь напрямую;
- аргументом передаётся `Suite::Test`;
- устанавливаются `YA_TEST_CONTEXT_FILE`, рабочий каталог теста и GDB path mappings
  для `/-S/` и `/-B/`.

## Сборка

```shell
./gradlew test buildPlugin verifyPlugin
```

Архив плагина появляется в `build/distributions`.
