package com.luzihao.utils;


import com.luzihao.api.Eumn.HttpStatusEnum;
import com.luzihao.api.result.Result;

public final class ResultUtil {
    private ResultUtil() {

    }

    /**
     * 成功
     *
     * @param httpStatusEnum
     * @return
     */
    public static Result successResult(HttpStatusEnum httpStatusEnum) {
        Result result = new Result();
        result.setCode(String.valueOf(httpStatusEnum.getCode()));
        result.setMsg(httpStatusEnum.getMsg());
        return result;
    }

    /**
     * 成功
     *
     * @param httpStatusEnum
     * @return
     */
    public static Result successResult(HttpStatusEnum httpStatusEnum, Object object) {
        Result result = new Result();
        result.setCode(String.valueOf(httpStatusEnum.getCode()));
        result.setMsg(httpStatusEnum.getMsg());
        result.setData(object);
        return result;
    }

    /**
     * 失败
     *
     * @param code 错误码
     * @param msg  错误消息
     * @return
     */
    public static Result errorResult(Integer code, String msg) {
        Result result = new Result();
        result.setCode(String.valueOf(code));
        result.setMsg(msg);
        return result;
    }

    /**
     * 失败
     *
     * @param httpStatusEnum
     * @return
     */
    public static Result errorResult(HttpStatusEnum httpStatusEnum) {
        Result result = new Result();
        result.setCode(String.valueOf(httpStatusEnum.getCode()));
        result.setMsg(httpStatusEnum.getMsg());
        return result;
    }

    public static Result errorResult(HttpStatusEnum httpStatusEnum, String msg) {
        Result result = new Result();
        result.setCode(String.valueOf(httpStatusEnum.getCode()));
        result.setMsg(msg);
        return result;
    }
}
