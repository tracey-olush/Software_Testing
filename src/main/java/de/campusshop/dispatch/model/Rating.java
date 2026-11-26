package de.campusshop.dispatch.model;

/** Whether a parcel could be priced. */
public enum Rating {
    /** The tariff was found and the charge computed. */
    RATED,
    /** The tariff could not be resolved (404, timeout, error); the parcel has no charge. */
    UNRATED
}
