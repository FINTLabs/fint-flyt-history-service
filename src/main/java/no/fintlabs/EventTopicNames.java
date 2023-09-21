package no.fintlabs;

public class EventTopicNames {

    private EventTopicNames() {
    }

    public static String INSTANCE_RECEIVED = "instance-received";
    public static String INSTANCE_REGISTERED = "instance-registered";
    public static String INSTANCE_REQUESTED_FOR_RETRY = "instance-requested-for-retry";
    public static String INSTANCE_MAPPED = "instance-mapped";
    public static String INSTANCE_READY_FOR_DISPATCH = "instance-ready-for-dispatch";
    public static String INSTANCE_DISPATCHED = "instance-dispatched";

    public static String INSTANCE_RECEIVAL_ERROR = "instance-receival-error";
    public static String INSTANCE_REGISTRATION_ERROR = "instance-registration-error";
    public static String INSTANCE_RETRY_REQUEST_ERROR = "instance-retry-request-error";
    public static String INSTANCE_MAPPING_ERROR = "instance-mapping-error";
    public static String INSTANCE_DISPATCHING_ERROR = "instance-dispatching-error";

}
