module org.terabit.common{
    requires kotlin.stdlib;
    requires leveldb;
    requires leveldb.api;
    requires core;
    requires io.netty.all;
    requires gson;
    requires com.google.common;
    requires commons.lang3;
    requires kotlinx.coroutines.core;

    exports org.terabit.common;
    exports org.terabit.core;
}