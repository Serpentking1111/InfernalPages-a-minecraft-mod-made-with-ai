package net.infernalpages.entity;

/**
 * The behavioural mode of a Mould of Souls.
 *
 * <ul>
 *   <li>{@link #PASSIVE} — the AI is fully disabled regardless of anything; the mould stands still.</li>
 *   <li>{@link #ACTIVE} — the AI only activates when an enemy comes within range; it then chases it
 *       and returns to its original position once the threat is gone.</li>
 *   <li>{@link #HUNT} — the mould hunts freely, as it does when enabled (follows the owner, wanders,
 *       and attacks anything except the owner).</li>
 * </ul>
 */
public enum GuardMode {
	PASSIVE("passive"),
	ACTIVE("active"),
	HUNT("hunt");

	private final String id;

	GuardMode(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static GuardMode fromId(String id) {
		for (GuardMode m : values()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		return HUNT;
	}
}
