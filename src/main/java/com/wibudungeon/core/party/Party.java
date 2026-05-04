package com.wibudungeon.core.party;

import java.util.*;

/**
 * Represents a player party for dungeon runs.
 * Contains the owner, members, and pending invites.
 */
public class Party {

    private final UUID owner;
    private final Set<UUID> members;
    private final Map<UUID, Long> pendingInvites;
    private final int maxSize;

    public Party(UUID owner, int maxSize) {
        this.owner = owner;
        this.members = new LinkedHashSet<>();
        this.members.add(owner);
        this.pendingInvites = new HashMap<>();
        this.maxSize = maxSize;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getSize() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    /**
     * Add a member to the party.
     *
     * @return true if added successfully, false if party is full
     */
    public boolean addMember(UUID uuid) {
        if (isFull()) return false;
        pendingInvites.remove(uuid);
        return members.add(uuid);
    }

    /**
     * Remove a member from the party. Cannot remove the owner.
     *
     * @return true if removed
     */
    public boolean removeMember(UUID uuid) {
        if (owner.equals(uuid)) return false;
        return members.remove(uuid);
    }

    /**
     * Send an invite to a player.
     */
    public void invite(UUID uuid) {
        pendingInvites.put(uuid, System.currentTimeMillis());
    }

    /**
     * Check if a player has a pending, non-expired invite.
     *
     * @param uuid         the player UUID
     * @param timeoutMillis timeout in milliseconds
     * @return true if invite is pending and not expired
     */
    public boolean hasPendingInvite(UUID uuid, long timeoutMillis) {
        Long time = pendingInvites.get(uuid);
        if (time == null) return false;
        if (System.currentTimeMillis() - time > timeoutMillis) {
            pendingInvites.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Remove a pending invite.
     */
    public void removeInvite(UUID uuid) {
        pendingInvites.remove(uuid);
    }

    /**
     * Get all member UUIDs as a new list.
     */
    public List<UUID> getMemberList() {
        return new ArrayList<>(members);
    }
}
