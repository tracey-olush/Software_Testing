package de.campusshop.dispatch.repository;

/**
 * Builds the S3 key of an archived manifest: {@code manifests/{carrierId}/{shipmentRef}.json}.
 */
public final class ManifestKeys {

    private ManifestKeys() {
    }

    public static String keyFor(String carrierId, String shipmentRef) {
        return "manifests/" + carrierId + "/" + shipmentRef + ".json";
    }
}
