package ssg.pex.scope;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScopeTracer {

    private final CopyOnWriteArrayList<ShadowRecord> history = new CopyOnWriteArrayList<>();

    public void recordShadow(ShadowRecord record) {
        history.add(record);
    }

    public List<ShadowRecord> history() {
        return Collections.unmodifiableList(history);
    }

    public List<ShadowRecord> historyFor(String variableName) {
        return history.stream()
                .filter(r -> r.variableName().equals(variableName))
                .toList();
    }

    public void clear() {
        history.clear();
    }
}
