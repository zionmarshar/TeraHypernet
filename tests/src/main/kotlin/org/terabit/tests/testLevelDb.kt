package org.terabit.tests

import okhttp3.OkHttpClient
import okhttp3.Request
import org.ethereum.util.ByteUtil
import org.ethereum.util.RLP
import org.iq80.leveldb.CompressionType
import org.iq80.leveldb.DB
import org.iq80.leveldb.impl.Iq80DBFactory
import org.json.JSONObject
import org.terabit.common.BLOCK_GAS_LIMIT_MIN
import org.terabit.common.DEFAULT_DIFFICULTY
import org.terabit.common.base64Dec
import org.terabit.common.getSha256
import org.terabit.core.*
import org.terabit.core.base.decodeTeraData
import org.terabit.core.state.StateDb
import org.terabit.db.ActivationStore
import org.terabit.db.BlockStore
import org.terabit.primary.NodeId
import org.terabit.primary.TERABIT_JKS_FILE_NAME
import org.terabit.primary.TERABIT_JKS_FILE_PWD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyStore
import java.util.*


val testAddr = arrayOf(
        "f678f67e38871e6209dccafe2d2a8c52b9bda3ea2373faf43efed0c44ccd56ae",
        "479725bad084247f3a5d5b709b4135d6a7d8f5d621e705be65eb81813da915a5",
        "501c876cd92541d93b87a74aec86ec9e95f164e70ce6f7b837551db73962486c",
        "aac5594c932ba58194047a311556380c08a333314d5755c58210d0b398e77b21",
        "0a2d552748713c43349e0df51a59e054db3e0f41cefcde2dc09d7045fff868df"  //tx to address
)
val testPubKey = arrayOf(
        "30820122300d06092a864886f70d01010105000382010f003082010a0282010100b47bf5256dda7d8a71b20cd9bece2f607175e24ba86257631270af425a44c51351c20ed344caad1df76eb7e9f7bf1e4e4ef908329cd9173d90e21e7ff6856a1f8f13ec96dc2efa767441fe58afd0de13afc879a4049e3765f1a97bcd445f334c8039ee5a8d51d806b2dc737a04d312ab37750f4b156a4bbf58843ade8427f76916e6d665157ab7e70adb19a2b45dff33f54e8b22709e11270bbf4765f9d0b78f5a198aa176a88be756486e1b43b322baeb08fe4541ddd5541e3562f1d925d6f72eae550a344e43faefe4483521003f57c9d6026b7f693f405fea02e0d0628b565b0520176bdc42feceb10dd8adc9f755fb5f4e6e8d1f6831b3848633acc6fe310203010001",
        "30820122300d06092a864886f70d01010105000382010f003082010a02820101008c969de9646af885196bb9ce17d6c52a81e9c7c2e3039b905313edff068290b92b0cbdd38b684d9ebf9c4e855a4936137e34ee5f6d93209740f1b3b9171657e76255b70af13753f6749964755726983ee4d487fe223c89cf2ea8de39a78bf78debc3d9ad677a6dbb42a14c4bb9ae5085ec555752cefdb925dce72d6e4c6ba5aa3223d773744418b9f6820a4fc1fb3a4d1cc059d8e2e4e78f3b139ac09f2e79f73a2ce05270f70cad96c06d568e909fb99ca310517b8c0a2e68191a8ea8684600a4a28d88d902e1b3eacdca32c1e41083c166c01d464063120e3f493c21138a1d75e3ca261751d40b5bc5efdeb4603386d4d55536e2eeb16da7c6efe87afc01290203010001",
        "30820122300d06092a864886f70d01010105000382010f003082010a02820101009b2c4a70d2be76e6a418b396f05969090da8d7e38a5743276cf5ab11f88f054223ee7c84b3971c2d9dff6cc6b39eb18474717118f0738e6f91adc0305266e5121eb8baf6a4c02154db650908b449813ca7fe559fb41cd092d681f904eea3dcf6e527e8920ac4a810073206d0cf7ca26b40694d058ea8869308d9c4f89487763a35c294442d5772d93873cf87813928f59cd0ac77aa381a89851c703b369cf4956acbb6d89547539d6c62a497ea2ef497d496db64870f3cf61fe51c23db7aeb8a683a5c88141514f9e17d41a9b95dfaffc703f7d88a0cbd6cf16c587cb030d3914fca8d6c38f290b588729b00e8a78f86e06bd029f2830a76eca9d0db27568a0d0203010001",
        "30820122300d06092a864886f70d01010105000382010f003082010a0282010100b849eccd6f6c57ada354a227ffc351938843ba06a42a2c00711e44ded0eccce4db9302b3719305fc0b412f75101094dab4b2213c59ecc5222daf3de461b1c9459ef75f36530fe734d2e78c97cb1308e1a91a0d66429ce58d000e025a510e65d384e7738e9c2ab0e60454564bf6ada363ce971fb4cc9028f048e594613beb0f3cd1e884bae6d90a57600917d8f3ebe9489b02ee953b10b2f61cb8d243514a40ef1e85bcbd24be29d84a536dd605b8d180eaab1d45672dde3aae8a5c2c717dea1bcfa8150df73b99335ce31a309fcadea2a13bcda07f8193f0ed16c3ae8421d36f2be0c910242251d4522b27c2d8fa90c9db3468903851a4c2c2683c5b1ebcf64b0203010001"
)
val testPriKey = arrayOf(
        "308204be020100300d06092a864886f70d0101010500048204a8308204a40201000282010100b47bf5256dda7d8a71b20cd9bece2f607175e24ba86257631270af425a44c51351c20ed344caad1df76eb7e9f7bf1e4e4ef908329cd9173d90e21e7ff6856a1f8f13ec96dc2efa767441fe58afd0de13afc879a4049e3765f1a97bcd445f334c8039ee5a8d51d806b2dc737a04d312ab37750f4b156a4bbf58843ade8427f76916e6d665157ab7e70adb19a2b45dff33f54e8b22709e11270bbf4765f9d0b78f5a198aa176a88be756486e1b43b322baeb08fe4541ddd5541e3562f1d925d6f72eae550a344e43faefe4483521003f57c9d6026b7f693f405fea02e0d0628b565b0520176bdc42feceb10dd8adc9f755fb5f4e6e8d1f6831b3848633acc6fe31020301000102820101009dd2773fcf9beb8390803db47aa119d49fe2918bcf268f7bd6b82ac93ede3be6f4eb58c36db83d41a3087252698dfa16480084269df947d55248289b851a8cfe23c32c738efe958dd7838dc2479bc1563db47489f46fd5c99354bd2975cc39b37fbd28ac8e3f2f444c4b71bf1550ed56431c5dcf2e5ed0f5f5954b55210341d86e37d5cae73c679273ccd6435bc63425703b32d46dcf14a5c3d423d75724b3ec5536ca3bdb46ecdbbd4fbf674181387ffb4e31fed0c0e1bb7d9904eeccb0a4d1b58fdbce1019db5f2587594374325ca150570b33854beb5b267a48b0d7dbba65af877e46a74441f0c929dd00b1879be921fd712cf3e466b3475549c77c47684102818100e95a89d550b27fa71012db28a12c8f8dfb7ef0809242ebe303b8bac6ef6db84c3563411f13714721eea34238ac67dc7256d6cc18c75bd444c6161a0547645c86b5964e1c9f91bda3a30470677c2feff05aa1c9ee175c4e31e939aa3ce8b63f8b3d3aee785230ece0adb1ee995d91ec82558d94b1e94dcae882041f28b690b7cd02818100c5ffecd8af5346d1576ecdbe974e069ea5ea892d3404e6e22e9cdc034039507cba61a11b27c163f515f8a19fcb8568c39a79dc345d347f7a17c2127ae2339d57f7015400ea3ca4ad5086b89322e27f189cdb2620aadce40449d0a35a0d7649adbb6fe59e25779eb65156262fcad48b79c2ea50eda1543d07cdcfbba9f68e73f5028181009a51718d4c0c7f36affe88485a2de9d16f5c07183fa159f58ae4834043de3f5ec67f98f5401a4d7c3720680eaa9514c7d87dbc0336f39770d3be91fea3b3cf2c96b18b7e8a580b1d48150b70a443df5d07d2ae8371976d6ebc5992272d8e387f904284a9b550fd95a7c85f8db4bf67b4d97703ba941b09f0b0eaa0e07fbb3c910281802b52e87c3140c38a22db77e17031fe78d04d155ef2b6efde25dce4b74162491e419a032a71f47aeeb1b40989717aeb85815390bf54759c3f16dbbc376f2f640a4f5dd707ac3940bf3b937ee793068450da6189623480c8d3b763139b2cbee9383e7f297b052fbb66a13d7c7590a8192cb75d95ffa90149a95f7b48d0d3c62fd102818040ed97609d300ef84cefb93e058def03f6018b874032675df9e8fd45acf38c29b0dfa7a24aa9f7be0fc8766eef5656acd5115f94e104b5ddeed3e090e8fdaecab8a40df350ea86fb5b4524b3b4de9efae1285004aacbfbd9a5feb29ba8c6ff72cea3f5f6109e036d2c51494ab9e40dce5cfc6adc2d48754175ac29fa591ffdbd",
        "308204bc020100300d06092a864886f70d0101010500048204a6308204a202010002820101008c969de9646af885196bb9ce17d6c52a81e9c7c2e3039b905313edff068290b92b0cbdd38b684d9ebf9c4e855a4936137e34ee5f6d93209740f1b3b9171657e76255b70af13753f6749964755726983ee4d487fe223c89cf2ea8de39a78bf78debc3d9ad677a6dbb42a14c4bb9ae5085ec555752cefdb925dce72d6e4c6ba5aa3223d773744418b9f6820a4fc1fb3a4d1cc059d8e2e4e78f3b139ac09f2e79f73a2ce05270f70cad96c06d568e909fb99ca310517b8c0a2e68191a8ea8684600a4a28d88d902e1b3eacdca32c1e41083c166c01d464063120e3f493c21138a1d75e3ca261751d40b5bc5efdeb4603386d4d55536e2eeb16da7c6efe87afc01290203010001028201002a565147afcde6f96e265ad60da7c7c87d4701b956d4074cf737d13c33a1ae23952b491f23a44d7bb908413d376b44b5aa141694f6529045fc70cd46b8052a83abc154561f3e2232ddfa339e829844d0ebe874ebbb33afec8c889e238ea93feba54c5d8932d853a348f57a1c73d662b971ea6159b6ac01cdbd308125ee2d9cbda7c98424a8d00f76b69521c204332d25a4749b938c0f73916204191ff90b05aa007c5b74463066a3b13ce59ab03c175acf01df3961d2865ab30c2f49fae7d9017ed13f769b482a13901c5deab3c90a2879f2a0be73244262b831d6aaeb433eeebe0fa59f0cc254c0bc0feeb48f81661891d3f0052dbd9321d9fca8868d82c40102818100e73ed87e4c06b89c512d4535949a4c4b0f0f2e0268174cfc87b6bb353f82c5b75e607f64c5be2432c0cbc93df582c638a100dce78b7cf806f4e454793b52d79c0603673a45fd87e795ef3445bd1a6bc899480efbeb993003dccd41aa95eb24410834efe94986000feeef980a893e9e2cacddcf3259727ad02550d4ea17070e91028181009ba35cca0748943960b07f7bd9e16113fc2034584ce5e407fde6f335377914cb3ed0e4b5325fa7c7363f0f168cedca43928ed19ed8c38634672eeeae75420061328fd063305a0fa880a9460903b39133e436d5d9ce6ccf236eb6d7faf2d8fc4d2951d3b7885b37df659092eb5514a4af3f9163dced1186d6348cb1d552b6c5190281800b4c54c386ef6521ae0daa2dfdfeb3ef8ee2ccfaad1f4651a847d349bcbaa4f25a98186152100230c09eb44c64ac6906e746e584229a8e7bb88757613cf6c536084d2e43f89b74826a1eefcce07a63fdbf0f783bf2bcdf4db1020e4d4959ddc197a70e79b56fe89cdeadb21e01aa9f8bcb3e631b30a7993c863636d0e123252102818014b1166001b1e38af150e5d208788f5881d7a23def134d4ac6b5d2b5cb873c7d7cff76b8a456d54e85e6c251c430c50850cece0da951ed60c02b1c2e73866fd87c4964721b768dc8603aa10607c8f2d00c5242023010854577adb56f36a4247ef9b4584a79456f5b32fc929bd8f6e2a22df85d677cd92cad03d584bb10c11dd90281805286d0b8294421254364542b87155556215da51853bac11a760a87d175e705256332cdf28e3e8a5d5ec48ab401aa3414d8b5f5a6ce205d50ba46082f8c2781270cbf930266b47b356d798863b3aea5b02ef3546fca80aaa0b4cf28f7ee2fe398477a6a2d8accd6a668f0678149edd2b1dc3ef5d514ecc92b5ebb706dfcb878be",
        "308204be020100300d06092a864886f70d0101010500048204a8308204a402010002820101009b2c4a70d2be76e6a418b396f05969090da8d7e38a5743276cf5ab11f88f054223ee7c84b3971c2d9dff6cc6b39eb18474717118f0738e6f91adc0305266e5121eb8baf6a4c02154db650908b449813ca7fe559fb41cd092d681f904eea3dcf6e527e8920ac4a810073206d0cf7ca26b40694d058ea8869308d9c4f89487763a35c294442d5772d93873cf87813928f59cd0ac77aa381a89851c703b369cf4956acbb6d89547539d6c62a497ea2ef497d496db64870f3cf61fe51c23db7aeb8a683a5c88141514f9e17d41a9b95dfaffc703f7d88a0cbd6cf16c587cb030d3914fca8d6c38f290b588729b00e8a78f86e06bd029f2830a76eca9d0db27568a0d02030100010282010003ccd8e1dc7767a80c5ac2832ecdb1c1d400cf90b6019f166717b921ce898a226d88d8a9d3501f138aec0778f24b2fa3c3ca476fc9ebba9046ec0f69df8abf8f6b0b3120fb35ed5b0ba874c92ec9220e1f3b1e53b3f084b723e3b8fa82f1a666b18401b119ec69054377d92ce1f7f66e53fc803363a402efb4e6c869221213361de1ad04e69be2d2c04a42562650080688c831ddb38d803c1ac43f7aec38f0ec653860d406119f7509de82606a91ee9a4f237f5d5ad78872c1536f89d65d24cec2600996f2f5ad44fdc3128cb82646fa55fa8c902d192e8b12f996631f0991392d816d3b83e957a90048d8227a69d7eacc8917504821c5069e106f09c19990a102818100e4fac47530e037650c56c7d4ac50c907b07612f8512809940b6b5718dbabfd8e50376eb0964bf01360dab6ef8387b6e9164b3fcd1b64d0dd9b97dfbd0c354d66a438d9c08c6ae5224171debd0ed7003a1a7c42271242d584d54f3547c98589f7457df7afe968e1f4dbcaaa75087a5abc85f1a442645869e909fa4beaf69217c902818100ad7be7a44c91ccd14d9b6a348ff963304e7d962a1b768008ef302e7aed11006279ada2878dcad102e529c8817db9b72f2eef2d1498a9bceac69fb84a6f187391793b6a60b92b818f5dae81bdd2158dbc1c5beb7d3494d959e9496bd38991e29ab8b28c5591981b28ada33f49ea03aeddbd8b94cc0ccd73fdd75a54428d114a2502818100a0dccf11a9718b5910ef125a056887a24044e314d226f77baafe10e524c6524e1d6aa3f7a8ced66d36b8d095b968a9865ca426c158425e3da16673c763013b4c2588cee9677c5d56d07547ceb7d33194a030b564af7e8674584ada1a81a7b002282ed3c820890f5caee05519fd07c020fb54845a2416bce8fb9a7ca527ee008902818100967237661248f67824773cd92c6a378c72e5124e9dd65c1e635f434fb33ae53c810d053fccc53010f4af1184ca853dc91b48abad8aded84839cbd54427a7eac1238355336021cc8f89cc861319973b7d0092ee5b7c38016da993d48074732132d02d76e9d60334ba482aa3fdf385122695f3f02c2c8617aaf11112b54c24cc490281803399c95ded3750a31018d591460fe7e6ef5f6970b95294281bfa3b29bd5a9e1875bdc9735b64760f2a4bd284a52e49eadcb58b873ec23dcbf0c209d4fc3877aa933d6f3d0f53d06fd093a324ec802bdab2efd017c63cec9cc45f1c4dfc762bb9ee6c7221c05cca2110ee308d73e5744b52e06db27af546faa19f2b8a6a8bb47d",
        "308204be020100300d06092a864886f70d0101010500048204a8308204a40201000282010100b849eccd6f6c57ada354a227ffc351938843ba06a42a2c00711e44ded0eccce4db9302b3719305fc0b412f75101094dab4b2213c59ecc5222daf3de461b1c9459ef75f36530fe734d2e78c97cb1308e1a91a0d66429ce58d000e025a510e65d384e7738e9c2ab0e60454564bf6ada363ce971fb4cc9028f048e594613beb0f3cd1e884bae6d90a57600917d8f3ebe9489b02ee953b10b2f61cb8d243514a40ef1e85bcbd24be29d84a536dd605b8d180eaab1d45672dde3aae8a5c2c717dea1bcfa8150df73b99335ce31a309fcadea2a13bcda07f8193f0ed16c3ae8421d36f2be0c910242251d4522b27c2d8fa90c9db3468903851a4c2c2683c5b1ebcf64b020301000102820101009e78d36d0537c15dcb69e7d745948bfdb5dba788367b1d437402776eb3722def4ef1e80caec9a307ce15cbdd7a6707d9da30a36be658385cdfb42620d1f1b4066e60823b90c9c824208884d9b303374bf5c2356b8e34a520cebf3d3eb51d106b078a90503df2681423fcf20878a370314f619d1e381a6f59d115bab6b273bbb136b11b579f184741bdb9b28b7c9de367c98719a68178af9807caab9cdcc63cd11d26684bb60c147b17b6d423cdec8ba7771eb819ef5696f4e36a829d4c83e2ff74ce57bc5e65de3c069606e15c1ceff70f5f85ecb89de0c90640da2a776dd52fb5cd16fa9b336abe6b45bb3a6ece3003949402c024903dc6949e0116e3c62f0102818100e9af0a21cff5dc6abe2ccf5aeecf4781f8d4a4f43ece3769c019f7613ae50e0749b86f296cfaf281f2760332ae59f0c384366458bceafb6b6e1abd06bbb720c4e096c0238ba8926cd656f09c92cbec22eb7799aa9725c145962b5ab295d0502d64bbffcc947abaf0327694664d4a1e5629dd37b8096b6b77d44365269a439d2b02818100c9e34e7a39af0e7b1064730f46c129bc6642b24cd332de9be66de396bbe01cf0d5b1c96a5eb61a9d2b77643aed097883f8430c6e87b2fc57514cfae3b847532091cab846b7145bee3dee17d8d45dfa215f92cad3a089c9843933e844fc6f6fae3d8e26d2dc11bfc406af2f9692721c47e1b626bdbb020cc7f2d444264df0bb6102818100e9ab63694e271e620a8a8a49f674849f9daf1cc7332a47d4d207e50b6fb5eda232005fffbf7eec122eddfcf2a7cf3de6740563b02c309879e35e8f57f54af85485f3986bb2d23ca0e26f8b2835cdfdf1291dae261bffdfd4728625aa600e2eae45858c130901f47e0d27efdaa03c963b389f8180e0c26e732368c4f3a5dc28830281802b66c373413d620690d2976dbc4f4072b1253b818a91d00b81f133d12ed892403020856569b10a6da96f844baab3b385e3743e92f077490a587f0d1cb17c1be1cf95de21cdb001c768e6eb9780a82762cf52166a8283bc9fc1506869943f3caa1faf94a7160a76c8648b7ad68aeb1f2b50d9c0e05ef8a4d8c35a60c03f5736610281806ee6073d0341718594da98da438a5bcccbd3c2756f37cf83d21cdda822db3c48bf5e9f658af5ed2b39c3ad67e044fd3008ebfdd9b6b97f8fe3e1d408f65e2e1fa8de50fd7bef60fc2b1855c1c6230939c58c5e45dfead940c9ab117c0b267b7c91768c4b3ebb4876885a18382d3f7b35f53841af6842829edfffbdb974a13a84"
)


fun main(){
//    val priKey = ByteUtil.hexStringToBytes("308204bc020100300d06092a864886f70d0101010500048204a6308204a20201000282010100ad8db7287d410dce823c9d75bc55a798b47fcc8ad1c447476b9711df345277c7362c1102f9d705bd2de1a3ca3f058be78a13ac30e0990f34239b896f353d10d4ca20d0e156b72056a8c14cb1a5a6d22942648dd4f50e45bbcb4c6b57d19fc936cfa9ef294eb7a58d1e374250c82c6667e2dbb2014410912c04d9ba6d62433109fddd1cacc071bed8ed8192a6fe1b3692f247f46bef3814725a94b51d900ed7a87ff9dc34b518f02682d80674487cdd0cf3379d486d5b75e0f65777405396fe4ec67c0c4917ea8fe7d9ffce7feb1ba1f004ad1ae5486167480e05d38ad5f05cba6a445d9c5438e8ea3ba8605e508a7f97458761ec5b5231942ae2354ea1e0b96d020301000102820100013ffc09b69d39543b6be4566cbcd130305c9b4484ae3d352e79300fc378b49422b7be879ef5057d8f4f08b6f42d3e0cb4d9df6b9ab27f8188a01e2546e4e1b226a285de9999480a57bccca89ae0d0b2ab5fbe05c29e0fb2828c52599790e70a1604cb4c28dcf9bc4273736ed182405a8f2fb5e07c750216a7124a5d6f4fbc92aacd014c0d05ebba3e4093efbdc6e066293db53cc82e8d56f3f5a1c2366f59232fa4c7f642f61e3c08646ca2de3e99bd5b412b226de817dc5e6334037b94a2aba69972753be868e581b6f38564df66853d60e3ea5def0fa7e7f76a33749ab942581ad3a58244152279f784bbb6ac98caeeeaa15880335478809a04824a587c0102818100e68cc21fc8504b8bffb6e8861f8af1b9bfd4e721bab777f1ec1ffd2549fb2cb4e435bcd92b863a94cda5159a67d6ce1a8ccd9bc14a237f4fa80b7178bc00b58805bc203cf4ffb9841f89ca365ce06d370885cdaf800659c8f8ce2cb0862ff6ec3f365b2ec0b9730f6f94cb5b0fc7ab8f32c10516ed8e7951e84929de2f2219e102818100c0b6444874e44c2924ab1ac4d24bd5a43d5e2ea6b933da99c2bfb80ddd183d1a3c5385d7c8484c4459d5184920efd464d80715457cf816dc04c43638a0336fac07bf593cc0660fed28db500841de3f390a2f471abee7351b7db8a54bc5e444af0e375d5d5a3a5c9a33214dd603d8cff2893fb64110917518754c82981ecc890d02818037869f1a587c3e35b198b3d7d9b0f516ef84af46085a37a124656fea932ed34d9dc8f37aba68d1a315043577c29a91790380618ae3f838a94ea7b2e489230295880c0f179f4a17bc227c651f42af2a9e9e86db4af162962d38ff2ef434dd48730da661bf98db11431e748fa596df580a5d06efdaddcbfa9c277db77fb38a5b01028180431a3444968f3805cb82d8db57ee98018c6cd309b2cb5f7ed2d5371d7cb307f3aeb5d69100684c46309186d74fe4b2ac3a9c9cc7897049656b33773dd86a90a3f23eaab000be89252768d64149ce3d6d7b22633be8e55114b57e29f06c83c376c1187e261758e30dbe680656531897aff8227b2fc4b92170d6c74161feb771b102818077fc2274b6e9f734c79ad483a54ae1cbead9a103616b9efa855aa84a6264ffa31707e2406f153b9116374b7ed5ed3d0f66c216f7eb5cc68b429972cb0dbecdc9278a23d62f3cc03bb29418549b8b99e880746ce557511e4f337d4685e870a94b6af027c30644e9d86da3cd0f0a1f4df7a1f042f5e974fca9fe561c486dd078b3")
//    val pubKey = ByteUtil.hexStringToBytes("58e487d134c4457926b7cf5eef6e0b1ad15f24511da36b7b483c12fdab764b50")
//    val signData = sign(priKey, pubKey)
//    val cred = Credential(signData, pubKey, 17)
//    cred.isMinorSeal = true
//    val data = cred.encode()
//    val rlp = RLP.decodeList(data)
//    val cred2 = Credential(rlp)
//    println("----$cred2")

//    val db = LevelDbDataSource("Test0/state")
//    val trie = TrieImpl(db)

//    showHeader(0)
//    println("-----------------------------------------------------------------------------------------------------\n\n")
//    showHeader(1)
//    println("-----------------------------------------------------------------------------------------------------\n\n")
//    showHeader(5230)


//    showState(ByteUtil.hexStringToBytes("76be8b528d0075f7aae98d6fa57a6d3c83ae480a8469e668d7b0af968995ac71"), false)
//    showState(ByteUtil.hexStringToBytes("63209b0e3bf04da1e6cb0294e849cd48dd866edec8c489e3f7912b4575967f11"), false)
//    showState(ByteUtil.hexStringToBytes("1326486b7d4c57039cb0b1ede2212f415f7f66a6983bb7459ff49ff7e6ca9df3"), false)
//    showState(ByteUtil.hexStringToBytes("865920c001128634a7636419cc5a06a33948bb0a241e5d5f7da213261b742d48"), false)

//    showState(ByteUtil.hexStringToBytes("4e7349595cf815e269e108674b45eb1c47f4fd2d1d6a68329cded9fc4f9fd772"), false)

//    showDb(0)

//    testBlock(2)

//    showBestHeader(0)


//    val priKey = ByteUtil.hexStringToBytes("308204bc020100300d06092a864886f70d0101010500048204a6308204a20201000282010100ad8db7287d410dce823c9d75bc55a798b47fcc8ad1c447476b9711df345277c7362c1102f9d705bd2de1a3ca3f058be78a13ac30e0990f34239b896f353d10d4ca20d0e156b72056a8c14cb1a5a6d22942648dd4f50e45bbcb4c6b57d19fc936cfa9ef294eb7a58d1e374250c82c6667e2dbb2014410912c04d9ba6d62433109fddd1cacc071bed8ed8192a6fe1b3692f247f46bef3814725a94b51d900ed7a87ff9dc34b518f02682d80674487cdd0cf3379d486d5b75e0f65777405396fe4ec67c0c4917ea8fe7d9ffce7feb1ba1f004ad1ae5486167480e05d38ad5f05cba6a445d9c5438e8ea3ba8605e508a7f97458761ec5b5231942ae2354ea1e0b96d020301000102820100013ffc09b69d39543b6be4566cbcd130305c9b4484ae3d352e79300fc378b49422b7be879ef5057d8f4f08b6f42d3e0cb4d9df6b9ab27f8188a01e2546e4e1b226a285de9999480a57bccca89ae0d0b2ab5fbe05c29e0fb2828c52599790e70a1604cb4c28dcf9bc4273736ed182405a8f2fb5e07c750216a7124a5d6f4fbc92aacd014c0d05ebba3e4093efbdc6e066293db53cc82e8d56f3f5a1c2366f59232fa4c7f642f61e3c08646ca2de3e99bd5b412b226de817dc5e6334037b94a2aba69972753be868e581b6f38564df66853d60e3ea5def0fa7e7f76a33749ab942581ad3a58244152279f784bbb6ac98caeeeaa15880335478809a04824a587c0102818100e68cc21fc8504b8bffb6e8861f8af1b9bfd4e721bab777f1ec1ffd2549fb2cb4e435bcd92b863a94cda5159a67d6ce1a8ccd9bc14a237f4fa80b7178bc00b58805bc203cf4ffb9841f89ca365ce06d370885cdaf800659c8f8ce2cb0862ff6ec3f365b2ec0b9730f6f94cb5b0fc7ab8f32c10516ed8e7951e84929de2f2219e102818100c0b6444874e44c2924ab1ac4d24bd5a43d5e2ea6b933da99c2bfb80ddd183d1a3c5385d7c8484c4459d5184920efd464d80715457cf816dc04c43638a0336fac07bf593cc0660fed28db500841de3f390a2f471abee7351b7db8a54bc5e444af0e375d5d5a3a5c9a33214dd603d8cff2893fb64110917518754c82981ecc890d02818037869f1a587c3e35b198b3d7d9b0f516ef84af46085a37a124656fea932ed34d9dc8f37aba68d1a315043577c29a91790380618ae3f838a94ea7b2e489230295880c0f179f4a17bc227c651f42af2a9e9e86db4af162962d38ff2ef434dd48730da661bf98db11431e748fa596df580a5d06efdaddcbfa9c277db77fb38a5b01028180431a3444968f3805cb82d8db57ee98018c6cd309b2cb5f7ed2d5371d7cb307f3aeb5d69100684c46309186d74fe4b2ac3a9c9cc7897049656b33773dd86a90a3f23eaab000be89252768d64149ce3d6d7b22633be8e55114b57e29f06c83c376c1187e261758e30dbe680656531897aff8227b2fc4b92170d6c74161feb771b102818077fc2274b6e9f734c79ad483a54ae1cbead9a103616b9efa855aa84a6264ffa31707e2406f153b9116374b7ed5ed3d0f66c216f7eb5cc68b429972cb0dbecdc9278a23d62f3cc03bb29418549b8b99e880746ce557511e4f337d4685e870a94b6af027c30644e9d86da3cd0f0a1f4df7a1f042f5e974fca9fe561c486dd078b3")
//    val pubKey = ByteUtil.hexStringToBytes("58e487d134c4457926b7cf5eef6e0b1ad15f24511da36b7b483c12fdab764b50")
//    val tx = createTransaction(ByteUtil.hexStringToBytes("76be8b528d0075f7aae98d6fa57a6d3c83ae480a8469e668d7b0af968995ac71"),
//            ByteUtil.hexStringToBytes("63209b0e3bf04da1e6cb0294e849cd48dd866edec8c489e3f7912b4575967f11"), 100L, 82L,
//            pubKey, priKey)
////    tx.isSuccess = ByteUtil.FAILED
////    val bytes = tx.encode()
////    val tx2 = Transaction(RLP.decodeList(bytes))
//
//    val rpt = TransactionReceipt(0, FAILED)
//    val info = TransactionInfo(rpt, pubKey, 0)
//    val bytes = info.encode()
//    val info2 = TransactionInfo(RLP.decodeList(bytes))
//    info2.receipt.getHash()


//    for (i in 0..5) {
//        testShow(i.toLong())
//    }
//    testShow(1L)

    readJks()
}

private fun testActivationDb() {
    val store = ActivationStore("Test0/activation")
    for (addr in testAddr) {
        val n = store.findActivation(ByteUtil.hexStringToBytes(addr))
        println("-----------n=$n")
    }
}

private fun testTxReceipt() {
    val db = openDb("Test0/txInfo") ?: return
    val itr = db.iterator()
    for (entry in itr) {
        println("----key=" + ByteUtil.toHexString(entry.key))
        println("----value=" + ByteUtil.toHexString(entry.value))
        println("----receipt=" + TransactionReceipt(RLP.decodeList(entry.value)) + "\n")
    }
    db.close()



    //val store = TransactionStore("Test0/txInfo")
}

private fun testShow(height: Long) {
    try {
        val client = OkHttpClient()

        val request = Request.Builder().url("http://127.0.0.1:9501/api/tx/get_result_by_hash?" +
                "hash=c3c73a3ea977edc7dd61cb35470bb571b0eda7c6d9907e475ea7d70876efaf36").build()

//        val request = Request.Builder().url("http://127.0.0.1:9501/api/block/get_by_height?height=$height&" +
//                "addr=f678f67e38871e6209dccafe2d2a8c52b9bda3ea2373faf43efed0c44ccd56ae").build()

//        val request = Request.Builder().url("http://127.0.0.1:9501/api/block/get_by_height?height=$height").build()
//        val request = Request.Builder().url("http://127.0.0.1:9501/api/block/get_best_height").build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return
        }
        val json = response.body?.string() ?: return
        println("----json: $json")
        val data = JSONObject(json).optString("data", null) ?: return
        val bytes = base64Dec(data)
        val terabit = decodeTeraData(bytes, TransactionReceipt::class.java)
//        for (tx in terabit.txList) {
//            println("-----------tx:${ByteUtil.toHexString(tx.getHash())}")
//        }
        println("--------------result=$terabit")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun showBestHeader(index: Int) {
    val store = BlockStore("Test$index/block")
    val block = store.getBestBlock() ?: return
    block.header.getHash()
    block.header.println()
    store.close()
}

private fun showHeader(height: Long) {
    val db = openDb("Test3/block") ?: return
    val hash = db.get(getBlockHashKey(height))
    println("--hash=${ByteUtil.toHexString(hash)}")
    val headerData = db.get(getHeaderKey(height, hash))
    val header = BlockHeader(headerData)
    header.getHash()
    header.println()
    db.close()
}

private fun showDb(index: Int) {
    println("-------------------------------------showDb--index--------------------------------------")
    val db = openDb("Test$index/txInfo") ?: return
    val itr = db.iterator()
    for (entry in itr) {
        println("----key=" + ByteUtil.toHexString(entry.key))
        println("----value=" + ByteUtil.toHexString(entry.value))
    }
    db.close()
}

private fun showState(root: ByteArray, modify: Boolean) {
    StateDb.init("Test1/state")
    println("--root=" + ByteUtil.toHexString(root))
    val db = StateDb.load(root)
    val addr1 = ByteUtil.hexStringToBytes(testAddr[0])
    val addr2 = ByteUtil.hexStringToBytes(testAddr[1])
    val addr3 = ByteUtil.hexStringToBytes(testAddr[2])
    val addr4 = ByteUtil.hexStringToBytes(testAddr[3])
    if (modify) {
        val obj1 = db.findOrCreateAccount(addr1)
        db.modifyCoin(obj1, 1000)
        val obj2 = db.findOrCreateAccount(addr2)
        db.modifyCoin(obj2, 1000)
        db.flush()
        println("--new root=" + ByteUtil.toHexString(db.getRoot()))
    }
    println("------------addr1=${db.getStateObject(addr1)}")
    println("------------addr2=${db.getStateObject(addr2)}")
    println("------------addr3=${db.getStateObject(addr3)}")
    println("------------addr4=${db.getStateObject(addr4)}\n\n")

    db.close()
}

private fun getPath(name: String): Path {
    return Paths.get("./dbs/$name.db")
}

private val factory = Iq80DBFactory()
fun openDb(name: String): DB? {
    val options: org.iq80.leveldb.Options = org.iq80.leveldb.Options()
    options.createIfMissing(true)
    options.compressionType(CompressionType.NONE)
    options.blockSize(10 * 1024 * 1024)
    options.writeBufferSize(10 * 1024 * 1024)
    options.cacheSize(0)
    options.paranoidChecks(true)
    options.verifyChecksums(true)
    options.maxOpenFiles(32)
    try {
        val dbPath: Path = getPath(name)
        if (!Files.isSymbolicLink(dbPath.parent)) {
            Files.createDirectories(dbPath.parent)
        }
        return try {
            factory.open(File(dbPath.toString()), options)
        } catch (e: IOException) {
            if (e.message!!.contains("Corruption:")) {
                factory.repair(dbPath.toFile(), options)
                factory.open(dbPath.toFile(), options)
            } else {
                throw e
            }
        }
    } catch (ioe: IOException) {
        ioe.printStackTrace()
        throw RuntimeException("Can't initialize database", ioe)
    }
}

fun testBlock(index: Int) {
    StateDb.init("Test$index/state")
    val blockStore = BlockStore("Test$index/block")

    val parent = blockStore.getChainBlockByHeight(16) ?: return
    NodeId = 0
    showBlock(parent)
    val txList = LinkedList<Transaction>()
    txList.add(createTx(2, 5))
    txList.add(createTx(1, 6))
    txList.add(createTx(3, 7))
    val block = FinalBlock(parent.getHash(), ByteUtil.hexStringToBytes(testAddr[index]),
            1595993891L, parent.header.height + 1, DEFAULT_DIFFICULTY, BLOCK_GAS_LIMIT_MIN,
            "Test$index".toByteArray(), txList)
    val state = applyBlock(parent, block)
    block.header.stateRoot = state.getRoot()
    block.header.minorRound = 1000000000000L
    block.getHash()
    showBlock(block)

    println("------------addr0=${state.getStateObject(ByteUtil.hexStringToBytes(testAddr[0]))}")
    println("------------addr1=${state.getStateObject(ByteUtil.hexStringToBytes(testAddr[1]))}")
    println("------------addr2=${state.getStateObject(ByteUtil.hexStringToBytes(testAddr[2]))}")
    println("------------addr3=${state.getStateObject(ByteUtil.hexStringToBytes(testAddr[3]))}\n\n")
}

fun createTx(index: Int, nonce: Long): Transaction {
    return createTransaction(nonce, 100, 1, 20000,
            ByteUtil.hexStringToBytes(testPriKey[index]),
            ByteUtil.hexStringToBytes(testPubKey[index]),
            ByteUtil.hexStringToBytes(testAddr[4]))
}

private fun readJks() {
    val ks = KeyStore.getInstance("JKS")
    try{
        val file = File(TERABIT_JKS_FILE_NAME)
        if(!file.exists()){
            println("------------------no jks file")
            return
        }
        ks.load(FileInputStream(file), TERABIT_JKS_FILE_PWD.toCharArray())
    }catch (e:Exception){
        e.printStackTrace()
    }

    for (alias in ks.aliases()) {
        println("----alias=$alias")
    }
    println("---------------------------------------")

    try {
        for (i in 0..9) {
            val proPass = KeyStore.PasswordProtection("111111".toCharArray())
            val pkEntry = ks.getEntry("test$i", proPass) as KeyStore.PrivateKeyEntry

            val pubKey = pkEntry.certificate.publicKey.encoded
            println("--addr=${ByteUtil.toHexString(getSha256(pubKey))}")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}