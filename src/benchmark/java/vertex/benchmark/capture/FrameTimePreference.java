package vertex.benchmark.capture;

/** Selects the frame-time type that the parser reads. */
public enum FrameTimePreference
{
    /** Reads CPU or Present() frame times. */
    PRESENTED,

    /** Reads display frame times. */
    DISPLAYED,

    /** Uses presented frame times when present, then display frame times. */
    AUTO
}
