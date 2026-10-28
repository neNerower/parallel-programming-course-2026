package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int ptr;

        public Replay(String trace) {
            this.trace = trace;
            this.ptr = 0;
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() {
            this.ptr = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            char targetName = trace.charAt(ptr++);
            int target = targetName - 'A';
            if (!enabled.contains(target)) {
                throw new IllegalStateException("Запрашиваемый поток " + targetName + "не активен");
            }
            return target;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) {
            this.rnd = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;

        private final List<Integer> path = new ArrayList<>();
        private final List<Integer> sizes = new ArrayList<>();

        private int depthPtr;

        public Dfs(int bound) {
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            this.depthPtr = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (depthPtr == path.size()) {
                path.add(0);
                sizes.add(enabled.size());
            }

            int choice = path.get(depthPtr);
            depthPtr++;

            return enabled.get(choice);
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            // От крайнего листа наверх до ближайшей развилки
            for (int i = path.size() - 1; i >= 0; i--) {
                int next = path.get(i) + 1;

                if (next < sizes.get(i)) {
                    // Меняем выбор на следующий вариант
                    path.set(i, next);

                    // Все последующие развилки нужно забыть
                    while (path.size() > i + 1) {
                        path.removeLast();
                        sizes.removeLast();
                    }

                    return true;
                }
            }

            // Везде перебраны все варианты
            return false;
        }
    }
}
