package com.jianzuo.writing;

import java.util.UUID;

/** An independently stored volume, including volumes with no chapters. */
public final class Section {
    public String id = UUID.randomUUID().toString(), notebook, name;
    public long rank;
    public Section(String notebook, String name, long rank) { this.notebook = notebook; this.name = name; this.rank = rank; }
}
