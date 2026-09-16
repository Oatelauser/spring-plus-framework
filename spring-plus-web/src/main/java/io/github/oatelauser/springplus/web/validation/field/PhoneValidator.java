package io.github.oatelauser.springplus.web.validation.field;


import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 校验手机号
 */
public class PhoneValidator implements ConstraintValidator<Phone, String> {

    //public static final String PHONE_REGEXP = "1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$";

    /**
     * 解释：
     * 1) 0\\d{2,3}-?\\d{7,8}         —— 区号 2~3 位，号码 7~8 位，'-' 可有可无
     * 2) (?:\\+?86)?1[3-9]\\d{9}     —— 中国大陆手机，可带 +86 / 86 / 不带
     * 3) \\+\\d{1,3}[1-9]\\d{4,14}   —— 其它国际区号手机（E.164 规范：最大 15 位）
     * 4) \\d{5,20}                   —— 其它国家或地区纯数字电话（5~20 位）
     */
    private static final String PHONE_REGEXP = "^(?:"
            + "0\\d{2,3}-?\\d{7,8}"          // 座机
            + "|(?:\\+?86)?1[3-9]\\d{9}"     // 中国大陆手机，可带 +86
            + "|\\+\\d{1,3}[1-9]\\d{4,14}"   // 其它国际区号的手机
            + "|\\d{5,20}"                   // 其它地区纯数字
            + ")$";

    public static final Pattern PATTERN = Pattern.compile(PHONE_REGEXP);

    /**
     * 校验手机号
     *
     * @param phone                      {@link  String}
     * @param constraintValidatorContext {@link  ConstraintValidatorContext}
     * @return {@link  Boolean}
     */
    @Override
    public boolean isValid(String phone, ConstraintValidatorContext constraintValidatorContext) {
        if (StringUtils.hasText(phone)) {
            return PATTERN.matcher(phone).matches();
        }
        return true;
    }

}
