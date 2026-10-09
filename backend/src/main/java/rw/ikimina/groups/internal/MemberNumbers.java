package rw.ikimina.groups.internal;

/** Member numbers as groups write them in their books: 001, 002, ... */
final class MemberNumbers {

    private MemberNumbers() {
    }

    static String format(long sequence) {
        return "%03d".formatted(sequence);
    }
}
