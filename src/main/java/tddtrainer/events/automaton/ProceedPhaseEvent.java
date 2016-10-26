package tddtrainer.events.automaton;

public class ProceedPhaseEvent {

    private boolean proceed;

    public ProceedPhaseEvent(boolean proceed) {
        this.proceed = proceed;
    }

    public boolean hasProceeded() {
        return proceed;
    }
}