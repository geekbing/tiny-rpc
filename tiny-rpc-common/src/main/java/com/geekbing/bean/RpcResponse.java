package com.geekbing.bean;

/**
 * 封装 RPC 响应
 *
 * @author bing
 */
public class RpcResponse {
    /**
     * 请求编号
     */
    private String requestId;

    /**
     * 异常信息
     */
    private Exception exception;

    /**
     * 响应结果
     */
    private Object result;

    /**
     * 是否带有异常
     */
    public boolean hasException() {
        return exception != null;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
