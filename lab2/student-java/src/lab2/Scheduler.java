package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();

    private final Semaphore events = new Semaphore(0);

    private final List<Worker> workers = new ArrayList<>();
    private final StringBuilder trace = new StringBuilder();

    private final List<SchedLock> locks = new ArrayList<>();

    private volatile boolean aborted;

    private static final class Worker {
        final Scheduler scheduler;
        final int id;
        final Runnable body;

        // Разрешение этому потоку продолжить выполнение
        final Semaphore permit = new Semaphore(0);

        Thread thread;
        boolean finished;

        // Была ли общая операция после последней паузы
        boolean touched;

        BooleanSupplier waitingFor;

        Throwable failure;

        Worker(Scheduler scheduler, int id, Runnable body) {
            this.scheduler = scheduler;
            this.id = id;
            this.body = body;
        }
    }

    /*
     * Внутреннее исключение для завершения потока, если планировщик прервал прогон.
     */
    private static final class StopRun extends RuntimeException {
    }

    public Scheduler() {
    }

    public void spawn(Runnable body) {
        workers.add(new Worker(this, workers.size(), body));
    }

    public void run(Strategy strategy) {
        try {
            // Запускаем потоки по одному и ждём, пока каждый остановится на стартовой паузе
            // Пользовательский body пока не выполняется
            for (Worker worker : workers) {
                worker.thread = new Thread(
                    () -> execute(worker),
                    "lab2-" + letter(worker.id)
                );

                worker.thread.start();
                events.acquireUninterruptibly();
            }

            int last = -1;

            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean allFinished = true;

                for (Worker worker : workers) {
                    if (worker.finished) {
                        continue;
                    }

                    allFinished = false;

                    if (worker.waitingFor == null
                        || worker.waitingFor.getAsBoolean()) {
                        enabled.add(worker.id);
                    }
                }

                if (allFinished) {
                    break;
                }

                /*
                 * Полное обнаружение deadlock с описанием локов —
                 * этап 2. Пока просто не допускаем зависания
                 */
                if (enabled.isEmpty()) {
                    deadlock = locks.stream()
                        .filter(lock -> !lock.isFree())
                        .map(lock -> "%s:%s".formatted(lock.name, letter(lock.getOwner())))
                        .collect(Collectors.joining(", "));
                    deadlock = "held={" + deadlock + "}";
                    break;
                }

                int chosen = strategy.pick(enabled, last);

                trace.append(letter(chosen));
                last = chosen;

                // Разрешаем выбранному потоку сделать следующий шаг
                workers.get(chosen).permit.release();

                // Ждём его следующей паузы или завершения
                events.acquireUninterruptibly();
            }
        } finally {
            aborted = true;

            for (Worker worker : workers) {
                worker.permit.release();
            }

            boolean interrupted = false;

            for (Worker worker : workers) {
                if (worker.thread == null) {
                    continue;
                }

                while (true) {
                    try {
                        worker.thread.join();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void execute(Worker worker) {
        CURRENT.set(worker);

        try {
            // Стартовая пауза: ждём первого решения стратегии
            pause(worker);
            worker.body.run();
        } catch (StopRun ignored) {
            // Планировщик завершает прогон.
        } catch (Throwable failure) {
            worker.failure = failure;
            failure.printStackTrace();
        } finally {
            worker.finished = true;
            CURRENT.remove();
            // Завершение тоже возвращает управление планировщику
            events.release();
        }
    }

    /*
     * Реальная передача управления:
     * сообщаем планировщику о паузе и засыпаем
     */
    private void pause(Worker worker) {
        events.release();
        worker.permit.acquireUninterruptibly();

        if (aborted) {
            throw new StopRun();
        }

        worker.touched = false;
    }

    void registerLock(SchedLock lock) {
        locks.add(lock);
    }

    public static void lock(SchedLock lock) {
        Worker worker = currentWorker();

        if (worker.touched) {
            worker.scheduler.pause(worker);
        }

        while (true) {
            if (lock.isFree()) {
                lock.setOwner(worker.id);
                logOperation("lock " + lock.name);
                worker.touched = true;
                return;
            }

            try {
                worker.waitingFor = lock::isFree;
                worker.scheduler.pause(worker);
            } finally {
                worker.waitingFor = null;
            }
        }
    }

    public static void unlock(SchedLock lock) {
        Worker worker = currentWorker();

        if (worker.touched) {
            worker.scheduler.pause(worker);
        }

        if (lock.getOwner() != worker.id) {
            throw new IllegalStateException();
        }

        lock.setOwner(-1);
        logOperation("unlock " + lock.name);
        worker.touched = true;
    }

    /*
     * Вызывается в Shared перед чтением или записью
     */
    static void beforeSharedAccess() {
        Worker worker = currentWorker();

        if (worker.touched) {
            worker.scheduler.pause(worker);
        }

        worker.touched = true;
    }

    static void logOperation(String operation) {
        Worker worker = currentWorker();
        Scheduler scheduler = worker.scheduler;

        if (scheduler.logging) {
            scheduler.log.add(letter(worker.id) + "  " + operation);
        }
    }

    private static Worker currentWorker() {
        return CURRENT.get();
    }

    private static char letter(int id) {
        return (char) ('A' + id);
    }

    public String traceString() {
        return trace.toString();
    }

    public static void point() {
        Worker worker = currentWorker();

        // Требуем паузу перед следующей общей операцией
        worker.touched = true;

        logOperation("window");
    }

    public static void await(BooleanSupplier cond) {
        Worker worker = currentWorker();

        boolean ready = cond.getAsBoolean();

        if (worker.touched || !ready) {
            worker.waitingFor = cond;

            try {
                worker.scheduler.pause(worker);
            } finally {
                worker.waitingFor = null;
            }
        }

        logOperation("await done");

        worker.touched = true;
    }
}