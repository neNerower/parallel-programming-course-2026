package lab2;

/* Этап 1: общая переменная. Перед каждым get/set — точка переключения (пауза планировщика),
 * после — строка в лог: чтение "<имя> -> <значение>", запись "<имя> = <значение>".
 * getAcquire/setRelease для планировщика ведут себя так же, как get/set. */
public final class Shared<T> {
    private final String name;
    private T value;

    public Shared(String name, T value) {
        this.name = name;
        this.value = value;
    }

    /** Чтение: при необходимости перед ним Scheduler приостанавливает поток. */
    public T get() {
        Scheduler.beforeSharedAccess();

        T result = value;
        Scheduler.logOperation(name + " -> " + result);

        return result;
    }

    /** Запись: при необходимости перед ней Scheduler приостанавливает поток. */
    public void set(T v) {
        Scheduler.beforeSharedAccess();

        value = v;
        Scheduler.logOperation(name + " = " + v);
    }

    /** На этом этапе ведёт себя так же, как get(). */
    public T getAcquire() {
        return get();
    }

    /** На этом этапе ведёт себя так же, как set(v). */
    public void setRelease(T v) {
        set(v);
    }

    /** Чтение без паузы и без лога: для проверок после прогона и условий await. */
    public T peek() { return value; }
}