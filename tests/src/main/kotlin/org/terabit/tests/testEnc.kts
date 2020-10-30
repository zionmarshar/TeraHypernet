import org.terabit.common.aesDec
import org.terabit.common.aesEnc

val pwd = "123456"
val enced = aesEnc("abc", pwd)
val deced = aesDec(enced,pwd)
println(enced)
println(deced)

