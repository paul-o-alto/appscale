package com.google.appengine.api.taskqueue.dev;

import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

import com.google.appengine.repackaged.com.google.io.protocol.ProtocolMessage;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.Request;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.Response;
import com.google.apphosting.api.ApiProxy;
import com.google.appengine.api.taskqueue.TaskQueuePb;
import com.google.apphosting.utils.config.AppEngineWebXml;

public class AppScaleTaskQueueClient {
    private static final Logger logger = Logger.getLogger(AppScaleTaskQueueClient.class.getName());
    private final int port = 17446;
    private final int MAX_TOTAL_CONNECTIONS = 200;
    private final int MAX_CONNECTIONS_PER_ROUTE = 20;
    private final int MAX_CONNECTIONS_PER_ROUTE_LOCALHOST = 80;
    private final int INPUT_STREAM_SIZE = 10240;
    private final String APPDATA_HEADER = "AppData";
    private final String SERVICE_NAME = "taskqueue";
    private final String PROTOCOL_BUFFER_HEADER = "ProtocolBufferType";
    private final String PROTOCOL_BUFFER_VALUE = "Request";
    private DefaultHttpClient client = null;
    private String url = null;
    private String appId = null;

    public AppScaleTaskQueueClient() {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), port));
        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(schemeRegistry);
        connManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        connManager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);
        String host = getTaskQueueProxy();
        url = "http://" + host + ":" + port + "/";
        HttpHost localhost = new HttpHost(url);
        connManager.setMaxForRoute(new HttpRoute(localhost), MAX_CONNECTIONS_PER_ROUTE_LOCALHOST);

        client = new DefaultHttpClient(connManager);
    }

    public TaskQueuePb.TaskQueueAddResponse add(TaskQueuePb.TaskQueueAddRequest addRequest) {
        addRequest.setAppId(getAppId());
        String taskPath = addRequest.getUrl();
        String appScaleTaskPath = "http://" + getNginxHost() + ":" + getNginxPort() + taskPath;
        addRequest.setUrl(appScaleTaskPath);
        Request request = new Request();
        request.setMethod("Add");
        request.setServiceName(SERVICE_NAME);
        request.setRequestAsBytes(addRequest.toByteArray());
        Response response = sendRequest(request);
        if (response.hasApplicationError()) {
            throw new ApiProxy.ApplicationException(
                response.getApplicationError().getCode(),
                "TaskQueue Add operation failed"
            );
        }
        TaskQueuePb.TaskQueueAddResponse addResponse = new TaskQueuePb.TaskQueueAddResponse();
        addResponse.parseFrom(response.getResponseAsBytes());
        return addResponse;
    }

    public TaskQueuePb.TaskQueueQueryAndOwnTasksResponse lease(TaskQueuePb.TaskQueueQueryAndOwnTasksRequest leaseRequest) {
        Request request = new Request();
        request.setMethod("QueryAndOwnTasks");
        request.setServiceName(SERVICE_NAME);
        request.setRequestAsBytes(leaseRequest.toByteArray());
        Response response = sendRequest(request);
        if (response.hasApplicationError()) {
            throw new ApiProxy.ApplicationException(
                response.getApplicationError().getCode(),
                "TaskQueue QueryAndOwn operation failed"
            );
        }
        TaskQueuePb.TaskQueueQueryAndOwnTasksResponse leaseResponse = new TaskQueuePb.TaskQueueQueryAndOwnTasksResponse();
        leaseResponse.parseFrom(response.getResponseAsBytes());
        return leaseResponse;
    }

    public TaskQueuePb.TaskQueueModifyTaskLeaseResponse modifyLease(TaskQueuePb.TaskQueueModifyTaskLeaseRequest modifyLeaseRequest) {
        Request request = new Request();
        request.setMethod("ModifyTaskLease");
        request.setServiceName(SERVICE_NAME);
        request.setRequestAsBytes(modifyLeaseRequest.toByteArray());
        Response response = sendRequest(request);
        if (response.hasApplicationError()) {
            throw new ApiProxy.ApplicationException(
                response.getApplicationError().getCode(),
                "TaskQueue ModifyTaskLease operation failed"
            );
        }
        TaskQueuePb.TaskQueueModifyTaskLeaseResponse modifyLeaseResponse = new TaskQueuePb.TaskQueueModifyTaskLeaseResponse();
        modifyLeaseResponse.parseFrom(response.getResponseAsBytes());
        return modifyLeaseResponse;
    }

    public TaskQueuePb.TaskQueueDeleteResponse delete(TaskQueuePb.TaskQueueDeleteRequest deleteRequest) {
        deleteRequest.setAppId(getAppId());
        Request request = new Request();
        request.setMethod("Delete");
        request.setServiceName(SERVICE_NAME);
        request.setRequestAsBytes(deleteRequest.toByteArray());
        Response response = sendRequest(request);
        if (response.hasApplicationError()) {
            throw new ApiProxy.ApplicationException(
                response.getApplicationError().getCode(),
                "TaskQueue Delete operation failed"
            );
        }
        TaskQueuePb.TaskQueueDeleteResponse deleteResponse = new TaskQueuePb.TaskQueueDeleteResponse();
        deleteResponse.parseFrom(response.getResponseAsBytes());
        return deleteResponse;
    }

    public TaskQueuePb.TaskQueuePurgeQueueResponse purge(TaskQueuePb.TaskQueuePurgeQueueRequest purgeRequest) {
        purgeRequest.setAppId(getAppId());
        Request request = new Request();
        request.setMethod("PurgeQueue");
        request.setServiceName(SERVICE_NAME);
        request.setRequestAsBytes(purgeRequest.toByteArray());
        Response response = sendRequest(request);
        if (response.hasApplicationError()) {
            throw new ApiProxy.ApplicationException(
                response.getApplicationError().getCode(),
                "TaskQueue PurgeQueue operation failed"
            );
        }
        TaskQueuePb.TaskQueuePurgeQueueResponse purgeQueueResponse = new TaskQueuePb.TaskQueuePurgeQueueResponse();
        purgeQueueResponse.parseFrom(response.getResponseAsBytes());
        return purgeQueueResponse;
    }

    private Response sendRequest(Request request) {
        HttpPost post = new HttpPost(url);
        post.addHeader(PROTOCOL_BUFFER_HEADER, PROTOCOL_BUFFER_VALUE);
        String tag = getAppId();
        post.addHeader(APPDATA_HEADER, tag);
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        try {
            bao.write(request.toByteArray());
            ByteArrayEntity entity = new ByteArrayEntity(bao.toByteArray());
            post.setEntity(entity);
            bao.close();
        } catch (IOException e) {
            logger.severe("Failed to create TaskQueue request due to IOException: " +
                e.getMessage());
            return null;
        }

        Response remoteResponse = new Response();
        ByteArrayResponseHandler handler = new ByteArrayResponseHandler();
        try {
            byte[] bytes = client.execute(post, handler);
            remoteResponse.parseFrom(bytes);
        } catch (ClientProtocolException e) {
            logger.severe("Failed to send TaskQueue request due to ClientProtocolException: " +
                e.getMessage());
        } catch (IOException e) {
            logger.severe("Failed to send TaskQueue request due to IOException: " + e.getMessage());
        }
        return remoteResponse;
    }

    private String getNginxHost() {
        String nginxHost = System.getProperty("NGINX_ADDR");
        return nginxHost;
    }

    private String getNginxPort() {
        String nginxPort = System.getProperty("NGINX_PORT");
        return nginxPort;
    }

    private String getAppId() {
        if (this.appId == null) {
            this.appId = System.getProperty("APPLICATION_ID");
        }
        return this.appId;
    }

    private String getTaskQueueProxy() {
        String tqProxy = System.getProperty("TQ_PROXY");
        return tqProxy;
    }

    private byte[] inputStreamToArray(InputStream in) {
        int len;
        int size = INPUT_STREAM_SIZE;
        byte[] buf = null;
        try {
            if (in instanceof ByteArrayInputStream) {
                size = in.available();
                buf = new byte[size];
                len = in.read(buf, 0, size);
            } else {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                buf = new byte[size];
                while ((len = in.read(buf, 0, size)) != -1) {
                    bos.write(buf, 0, len);
                }
                buf = bos.toByteArray();

            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buf;
    }

    class ByteArrayResponseHandler implements ResponseHandler<byte[]> {

        public byte[] handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream inputStream = entity.getContent();
                try {
                    return inputStreamToArray(inputStream);
                } finally {
                    entity.getContent().close();
                }
            }
            return new byte[]{};
        }
    }
}
