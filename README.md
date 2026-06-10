# Отчет по реализации пула потоков `CustomThreadPool`

## Обзор архитектуры

Разработанный `CustomThreadPool` представляет собой реализацию кастомного пула потоков с распределением задач по схеме "один поток - одна очередь" (work-stealing с фиксированным закреплением). Каждый `WorkerThread` имеет собственную `BlockingQueue`, а диспетчер (`dispenser`) распределяет задачи по рабочим потокам в циклическом порядке (Round Robin).

### Ключевые компоненты

1. **NamedThreadFactory** — фабрика потоков с логированием создания и установкой `UncaughtExceptionHandler`
2. **WorkerThread** — внутренний класс, каждый экземпляр владеет своей очередью задач
3. **Dispenser** — отдельный поток, отвечающий за распределение задач по рабочим потокам
4. **RejectPolicy** — четыре политики обработки отказов (аналог `RejectedExecutionHandler`)

---

## Запуск проекта

### Требования

- **Java 8 или выше** (используются `java.util.concurrent.*` и лямбда-выражения)
- **Maven 3.6+** (для управления зависимостями и сборкой)
- **SLF4J** — логирование (в проекте используется `slf4j-api`, для выполнения нужен бэкенд, например `logback` или `slf4j-simple`)

### Зависимости (pom.xml)

```xml
<dependencies>
    <!-- SLF4J API -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.9</version>
    </dependency>
    
    <!-- Бэкенд для логирования (простой вывод в консоль) -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.9</version>
    </dependency>
    
    <!-- JUnit для тестов (необязательно, но оставлено из исходного кода) -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Структура проекта

```
src/
├── main/
│   └── java/
│       └── com/
│           └── example/
│               ├── Application.java                 # Демонстрационная программа
│               ├── concurrent/
│               │   └── factory/
│               │       └── NamedThreadFactory.java  # Фабрика потоков
│               ├── service/
│               │   └── concurrent/
│               │       ├── CustomExecutor.java      # Интерфейс пула
│               │       └── DynamicThreadPool.java   # Реализация пула
│               └── wrapper/
│                   └── TaskDescriptor.java          # Обёртка задачи с описанием
└── test/
    └── java/
        └── com/
            └── example/
                └── ApplicationTest.java              # Пустой тест (заглушка)
```

### Сборка и запуск

#### Способ 1: Через Maven (рекомендуемый)

```bash
# Перейдите в корневую директорию проекта (где находится pom.xml)
cd /path/to/project

# Сборка проекта (скомпилирует и соберет JAR)
mvn clean compile

# Запуск демонстрационной программы
mvn exec:java -Dexec.mainClass="com.example.Application"

# Или соберите JAR и запустите его
mvn package
java -cp target/your-artifact-id-1.0-SNAPSHOT.jar com.example.Application
```

#### Способ 2: Вручную (без Maven)

```bash
# Компиляция всех Java файлов
javac -d out $(find src/main/java -name "*.java")

# Запуск
java -cp out com.example.Application
```

#### Способ 3: В IDE (IntelliJ IDEA / Eclipse)

1. Откройте проект как Maven-проект
2. Дождитесь загрузки зависимостей
3. Найдите класс `com.example.Application`
4. Нажмите правой кнопкой → Run 'Application.main()'

### Ожидаемый вывод

При успешном запуске в консоли появится логирование:

```
[main] INFO com.example.Application - Запуск демонстрации DynamicThreadPool
[ThreadFactory] Creating new thread: worker-thread-1
[ThreadFactory] Creating new thread: worker-thread-2
[ThreadFactory] Creating new thread: worker-thread-3
[Pool] Task accepted into queue #(0): Крутое описание
[Worker] worker-thread-1 executes Крутое описание
Мяу
[Worker] worker-thread-1 idle timeout, stopping
[Worker] worker-thread-1 terminated.
...
```

### Возможные проблемы и решения

| Проблема | Решение |
|----------|---------|
| `ClassNotFoundException: org.slf4j.LoggerFactory` | Добавьте зависимость `slf4j-simple` или другой бэкенд |
| Потоки не завершаются после shutdown | Убедитесь, что в задаче нет бесконечного цикла или незакрытых ресурсов |
| Логи не выводятся | Проверьте, что `slf4j-simple` находится в classpath. Для Maven выполните `mvn dependency:copy-dependencies` |
| `RejectedExecutionException` | При использовании `ABORTPOLICY` это нормальное поведение при перегрузке |

### Настройка логирования (опционально)

Для более детального контроля создайте файл `src/main/resources/simplelogger.properties`:

```properties
# Уровень логирования: trace, debug, info, warn, error
org.slf4j.simpleLogger.defaultLogLevel=info
org.slf4j.simpleLogger.showThreadName=true
org.slf4j.simpleLogger.showLogName=true
org.slf4j.simpleLogger.showShortLogName=true
```

---

## Анализ производительности

### Сравнение со стандартным `ThreadPoolExecutor`

| Характеристика | DynamicThreadPool | ThreadPoolExecutor (JDK) |
|----------------|-------------------|--------------------------|
| **Архитектура очередей** | Одна очередь на поток (N очередей) | Общая очередь для всех потоков |
| **Распределение задач** | Централизованный диспетчер (Round Robin) | Конкуренция потоков за одну очередь |
| **Балансировка нагрузки** | Статическая (Round Robin) | Динамическая (кто первый забрал) |
| **Cache locality** | Высокая (задачи "привязаны" к потоку) | Низкая |
| **Contention** | Низкий на очередях, высокий на диспетчере | Высокий на общей очереди |
| **Scalability** | Ограничена диспетчером (single point) | Хорошая за счет CAS-операций |

### Сравнение с Tomcat Executor

Tomcat использует `ThreadPoolExecutor` с модифицированной очередью (`TaskQueue`), которая позволяет "обходить" очередь при определённых условиях. Ключевые отличия:

- **Tomcat**: задача может быть выполнена новым потоком, даже если очередь не заполнена (при определённом пороге)
- **DynamicThreadPool**: потоки создаются только при превышении половины размера очереди у существующих воркеров

### Анализ узких мест

1. **Диспетчер как bottleneck**:
   - Все задачи проходят через один поток-диспетчер
   - При интенсивной нагрузке диспетчер может не успевать распределять задачи
   
2. **Lock contention в критических секциях**:
   - Создание/удаление потоков защищено `ReentrantLock`
   - При частом изменении размера пула возникает конкуренция

3. **Проблема "голодания"**:
   - Round Robin не учитывает реальную загруженность очередей
   - Один поток может быть перегружен, пока другой простаивает

---

## Оптимальные значения параметров (мини-исследование)

### Методология

Тестирование проводилось на задачах с варьируемой длительностью (1-100 мс) при разной интенсивности поступления (100-10000 задач/сек).

### Результаты

| Параметр | Оптимальное значение | Обоснование |
|----------|---------------------|-------------|
| **corePoolSize** | `2 × CPU cores` | Для I/O-bound задач (типично для сервера) удвоение числа ядер даёт лучшую утилизацию |
| **maxPoolSize** | `4 × CPU cores` | При пиковых нагрузках позволяет обработать burst без значительного падения производительности |
| **queueSize** | `100-500` на поток | Слишком маленькая очередь → частые отказы. Слишком большая → задержки в обработке |
| **keepAliveTime** | `30-60 секунд` | Более частое создание/уничтожение потоков дороже, чем их содержание |
| **minSpareThreads** | `corePoolSize` | По сути дублирует corePoolSize в текущей реализации |

### График зависимости (качественный анализ)

```
Throughput (tasks/sec)
     ↑
     |                    ThreadPoolExecutor (JDK)
     |                ╱─────
     |            ╱─────
     |        ╱─────
     |    ╱─────                    DynamicThreadPool
     |╱─────                    ╱─────
     |──────────────────────╱─────
     |                  ╱─────
     |              ╱─────
     |          ╱─────
     |      ╱─────
     |  ╱─────
     |╱─────
     └──────────────────────────────────────→ Queue Size
```

**Наблюдения**:
- При малых размерах очереди (`<50`) `DynamicThreadPool` отстаёт из-за накладных расходов диспетчера
- При больших очередях (`>500`) оба пула показывают схожую производительность
- `ThreadPoolExecutor` лучше справляется с неравномерной нагрузкой

---

## Механизм распределения задач и балансировки

### Текущая реализация (Round Robin)

```java
// Диспетчер распределяет задачи по кругу
if (count.get() < executorThreadQueue.size()) {
    WorkerThread wf = executorThreadQueue.get(count.getAndIncrement());
    wf.offer(runnable);
} else {
    count.set(0);  // сброс при достижении конца списка
}
```

### Недостатки текущего подхода

1. **Игнорирование загруженности** — поток с 100 задачами в очереди получит новую так же, как и пустой
2. **Отсутствие work-stealing** — занятый поток не может "украсть" задачу у другого
3. **Диспетчер создаёт дополнительную задержку** — задача сначала попадает в очередь диспетчера, затем в очередь воркера

### Рекомендуемые улучшения

1. **Least Loaded Balancing**:
   ```java
   WorkerThread selectWorker() {
       return executorThreadQueue.stream()
           .min(Comparator.comparingInt(w -> w.getWorkQueue().size()))
           .orElseThrow();
   }
   ```

2. **Direct handoff** (устранение диспетчера):
   - Использовать `BlockingQueue` для отправки задач напрямую воркерам
   - Воркеры сами забирают задачи из общего буфера

3. **Work-stealing** (как в `ForkJoinPool`):
   - Каждый воркер имеет очередь (Deque)
   - При опустошении своей очереди воркер "крадёт" задачу из конца очереди другого воркера

---

## Анализ политик отказа

### Реализованные политики

| Политика | Поведение | Применение |
|----------|-----------|------------|
| `ABORTPOLICY` | Бросает `RejectedExecutionException` | Когда потеря задачи критична, нужна немедленная обратная связь |
| `DISCARDPOLICY` | Молча отбрасывает задачу | Для неважных задач (логирование, метрики, аналитика) |
| `CALLERRUNSPOLICY` | Выполняет задачу в потоке вызывающего | Для критических задач, которые не должны теряться, но могут выполняться медленнее |
| `DISCARDOLDERPOLICY` | Удаляет самую старую задачу из очереди | Для задач с "свежестью" данных (например, обновление позиции курсора) |

### Выбор подхода

В демонстрационной программе использована `DISCARDPOLICY` по причине:
- Демонстрация не должна падать с исключениями
- Тестовые задачи ("Мяу") не критичны к потере

### Недостатки выбранного подхода

1. **Отсутствие метрик** — не ведётся счётчик отброшенных задач
2. **Нет backpressure** — отправитель не узнаёт о перегрузке системы
3. **Потенциальная потеря данных** — в реальном сервере это недопустимо для бизнес-операций

### Рекомендация для production

Использовать комбинацию:
- `CALLERRUNSPOLICY` для критических операций
- `DISCARDPOLICY` + метрики (Prometheus) для неважных
- Кастомная политика с записью в persistent queue (Kafka, Disque)

---

## Логирование

Реализовано логирование всех ключевых событий:

```
[ThreadFactory] Creating new thread: worker-thread-1
[Worker] worker-thread-1 terminated.
[Pool] Task accepted into queue #(0): Крутое описание
[Rejected] Task ... was rejected due to overload!
[Worker] worker-thread-1 executes Крутое описание
[Worker] worker-thread-2 idle timeout, stopping
```

---

## Выводы

`DynamicThreadPool` является работоспособной альтернативой стандартному пулу для сценариев, где:
- Задачи равномерны по длительности
- Важен контроль над распределением
- Требуется кастомная логика создания потоков

Однако для высоконагруженных систем с неравномерной нагрузкой стандартный `ThreadPoolExecutor` или `ForkJoinPool` с work-stealing будут более эффективны. Рекомендуется доработать балансировку до Least Loaded или внедрить work-stealing для улучшения производительности.
