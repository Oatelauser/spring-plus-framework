package io.github.oatelauser.springplus.calcite.memory.mybatis;

/**
 * MyBatis Mapper 结果 POJO：演示 {@code mapUnderscoreToCamelCase}（user_id -&gt; userId）与显式 resultMap 映射。
 * 提供无参构造与 setter，满足 MyBatis 反射式结果映射要求。
 */
public class OrderView {

    private Integer id;
    private Integer userId;
    private Integer amount;
    private String status;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
