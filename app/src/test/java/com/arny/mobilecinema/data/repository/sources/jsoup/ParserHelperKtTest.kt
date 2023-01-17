package com.arny.mobilecinema.data.repository.sources.jsoup

import com.arny.mobilecinema.data.models.PageParserSelectors
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Test

internal class ParserHelperKtTest {
    @Test
    fun getInfo() = runBlocking {
        val element = Element("div").append(
            "<table>\n" +
                    "\t\t<tbody><tr><td>Обновлен:</td><td>00:11</td></tr>\n" +
                    "\t\t<tr><td>Оригинал:</td><td>Source Code</td></tr> \n" +
                    "\t\t \n" +
                    "\t\t<tr><td>Год:</td><td><a href=\"/films/god-2011\">2011</a></td></tr> \n" +
                    "        <tr><td>Качество:</td><td><a style=\"color: #008000;\" href=\"/films/formats\">BDRip(отличное)</a></td></tr>\t\t\n" +
                    "\t\t<tr><td>Перевод:</td><td>Дублированный</td></tr> \n" +
                    "\t\t<tr><td>Время:</td><td>01:33:28</td></tr>\n" +
                    "\t\t<tr><td>Страна:</td><td><a href=\"/films/c132\">США</a></td></tr> \n" +
                    "\t\t<tr><td>Жанр:</td><td><a href=\"/films/r31\">фантастика</a>, <a href=\"/films/r3\">боевик</a>, <a href=\"/films/r29\">триллер</a>, <a href=\"/films/r10\">драма</a></td></tr>\n" +
                    "\t\t \n" +
                    "\t\t<tr><td>Режиссер:</td><td>Дункан Джонс</td></tr> \n" +
                    "\t\t<tr><td>Актеры:</td><td>Джейк Джилленхол,Мишель Монахэн,Вера Фармига,Джеффри Райт,Майкл Арден,Кэс Анвар,Расселл Питерс,Брент Скэгфорд,Крэйг Томас,Гордон Мастен</td></tr>\n" +
                    "\t\t<tr><td>Скачали:</td><td>67439 раз (4612 сегодня)</td></tr> \t\t\n" +
                    "\t\t</tbody></table>"
        )
        val info = getPageInfo(element, PageParserSelectors(infoSelector = "tr"))
        assert(info.contains("Перевод"))
    }

    @Test
    fun getUrl() = runBlocking {
        val element = Document("/").append(
            "\n" +
                    "<!DOCTYPE html>\n" +
                    "<html lang=\"ru\" prefix=\"og: http://ogp.me/ns#\">\n" +
                    "<head>\n" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n" +
                    "<meta charset=\"UTF-8\"/><title>Скачать фильм Исходный код 2011г на телефон или планшет бесплатно</title><meta name=\"keywords\" content=\"Source Code, 2011, скачать по частям, смотреть онлайн, скачать на android, скачать кино, скачать 3gp mp4, США, фантастика, боевик, триллер, драма\" /><meta name=\"description\" content=\"Исходный код (Source Code) - Солдат по имени Коултер мистическим образом оказывается в теле неизвестного мужчины, погибшего в железнодорожной катастрофе. Коултер вынужден пер...\" />\n" +
                    "<meta property=\"og:title\" content=\"Исходный код (2011) - скачать для планшета или телефона\" />\n" +
                    "<meta property=\"og:url\" content=\"https://mi.anwap.tube/films/5813\" />\n" +
                    "<meta property=\"og:image\" content=\"https://mi.anwap.tube/films/screen/5813.gif\" />\n" +
                    "<meta property=\"og:desc\" content=\"Солдат по имени Коултер мистическим образом оказывается в теле неизвестного мужчины, погибшего в железнодорожной катастрофе. Коултер вынужден переживать чужую смерть снова и снова до тех пор, пока не поймет, кто — зачинщик катастрофы.\" />\n" +
                    "<meta property=\"og:type\" content=\"video.movie\" />\n" +
                    "<meta property=\"og:duration\" content=\"5608\" />\n" +
                    "\n" +
                    "<link rel=\"icon\" href=\"/style/favicon.ico\" type=\"image/x-icon\"/>\n" +
                    "<link rel=\"shortcut icon\" href=\"/style/favicon.ico\" type=\"image/x-icon\"/>\n" +
                    "<meta name=\"theme-color\" content=\"#649900\">\n" +
                    "<link rel=\"manifest\" href=\"/manifest.webmanifest\">\n" +
                    "<meta name=\"msapplication-config\" content=\"/style/images/ico/browserconfig.xml\">\n" +
                    "<link rel=\"icon\" type=\"image/png\" sizes=\"16x16\" href=\"/style/images/ico/icon16.png\">\n" +
                    "<link rel=\"icon\" type=\"image/png\" sizes=\"32x32\" href=\"/style/images/ico/icon32.png\">\n" +
                    "<link rel=\"icon\" type=\"image/png\" sizes=\"128x128\" href=\"/style/images/ico/icon128.png\">\n" +
                    "<link rel=\"icon\" type=\"image/png\" sizes=\"180x180\" href=\"/style/images/ico/icon180.png\">\n" +
                    "<link rel=\"icon\" type=\"image/png\" sizes=\"192x192\" href=\"/style/images/ico/icon192.png\">\n" +
                    "<link rel=\"apple-touch-icon\" sizes=\"192x192\" href=\"/style/images/ico/apple-touch-icon.png\">\n" +
                    "<link rel=\"stylesheet\" type=\"text/css\" href=\"/style/winter.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"/style/films.css\"/><style>\n" +
                    ".lmzkeezb,.pvhbjywg{ margin:0 2px 2px; padding: 2px;border-radius:0 0 8px 8px;border-bottom: 3px solid #FFFFFF;  border-top: 1px solid #FFFFFF;\n" +
                    "background: url(/style/img/hot.png) no-repeat right bottom, url(/style/img/hh.png) repeat-x top #c2e5fe;\n" +
                    "min-height: 62px}\n" +
                    ".lmzkeezb img,.pvhbjywg img {border-radius: 8px;box-shadow: 0 1px 2px #000; float: left;}\n" +
                    ".lmzkeezb a,.pvhbjywg a{ color:#000; text-shadow: 0 1px 2px  #fff}\n" +
                    "</style><link rel=\"canonical\" href=\"https://mi.anwap.tube/films/5813\" /></head><body><div class=\"logo\"><a href=\"https://m.anwap.love\" title=\"Anwap\"></a></div>\n" +
                    "<div class=\"chislo\"><span><img src=\"/smiles/salut3_545.gif\" height=\"15\" alt=\"\" />Градиент позитива<img src=\"/smiles/salut3_545.gif\" height=\"15\" alt=\"\" /></span></div><div style=\"text-align: center;\"><div class=\"save\"><div class=\"zag2\"><a href=\"https://m.anwap.love/enter\">Вход</a> |<a href=\"https://m.anwap.love/reg\">Регистрация</a></div></div></div><div class=\"ball\"><h1 class=\"blc\"><span class=\"acat\"><img src=\"/style/img/polez.png\" alt=\"\" />Исходный код</span></h1></div><div class=\"blm\"><script>\n" +
                    "function view(n) { style = document.getElementById(n).style; style.display = (style.display == 'block') ? 'none' : 'block'; }\n" +
                    "</script>\n" +
                    "<div class=\"filmopis screen\">\n" +
                    "<img src=\"https://mi.anwap.tube/films/screen/5813.jpg\" loading=\"lazy\"  alt=\"Исходный код / Source Code\" title=\"Исходный код / Source Code скачать на телефон бесплатно\" class=\"filmscreen\" /> <div class=\"rating\" id=\"rating\">\n" +
                    "<span class=\"knopka like\" onclick=\"return alert('Для голосования необходимо зарегистрироваться на сайте или войти в свой аккаунт, если уже зарегистрированны!');\">174</span><span class=\"knopka dislike\" onclick=\"return alert('Для голосования необходимо зарегистрироваться на сайте или войти в свой аккаунт, если уже зарегистрированны!');\">9</span>\n" +
                    " </div>\n" +
                    "\n" +
                    "\t<script>\n" +
                    "\tfunction Rate(id, uid, hash, type) {var xhr = new XMLHttpRequest();xhr.open(\"POST\", \"https://mi.anwap.tube/js/ajax/rating.php\", false);xhr.setRequestHeader(\"Content-Type\", \"application/x-www-form-urlencoded\");xhr.send(\"act=rating&vid=films&pid=\"+id+\"&uid=\"+uid+\"&hash=\"+hash+\"&type=\"+type);\n" +
                    "    if (xhr.status == 200) {document.getElementById(\"rating\").innerHTML = xhr.responseText;}}\n" +
                    "\t</script></div><div class=\"filmopis screen2\">\n" +
                    "\t\t<table>\n" +
                    "\t\t<tr><td>Обновлен:</td><td>00:11</td></tr>\n" +
                    "\t\t<tr><td>Оригинал:</td><td>Source Code</td></tr> \n" +
                    "\t\t \n" +
                    "\t\t<tr><td>Год:</td><td><a href=\"/films/god-2011\">2011</a></td></tr> \n" +
                    "        <tr><td>Качество:</td><td><a style=\"color: #008000;\" href=\"/films/formats\">BDRip(отличное)</a></td></tr>\t\t\n" +
                    "\t\t<tr><td>Перевод:</td><td>Дублированный</td></tr> \n" +
                    "\t\t<tr><td>Время:</td><td>01:33:28</td></tr>\n" +
                    "\t\t<tr><td>Страна:</td><td><a href=\"/films/c132\">США</a></td></tr> \n" +
                    "\t\t<tr><td>Жанр:</td><td><a href=\"/films/r31\">фантастика</a>, <a href=\"/films/r3\">боевик</a>, <a href=\"/films/r29\">триллер</a>, <a href=\"/films/r10\">драма</a></td></tr>\n" +
                    "\t\t \n" +
                    "\t\t<tr><td>Режиссер:</td><td>Дункан Джонс</td></tr> \n" +
                    "\t\t<tr><td>Актеры:</td><td>Джейк Джилленхол,Мишель Монахэн,Вера Фармига,Джеффри Райт,Майкл Арден,Кэс Анвар,Расселл Питерс,Брент Скэгфорд,Крэйг Томас,Гордон Мастен</td></tr>\n" +
                    "\t\t<tr><td>Скачали:</td><td>67552 раза (4725 сегодня)</td></tr> \t\t\n" +
                    "\t\t</table></div><div class=\"clear\"></div>\n" +
                    "\t\t<div class=\"filmopis screen3\"><p>Солдат по имени Коултер мистическим образом оказывается в теле неизвестного мужчины, погибшего в железнодорожной катастрофе. Коултер вынужден переживать чужую смерть снова и снова до тех пор, пока не поймет, кто — зачинщик катастрофы.</p><hr/><div class=\"serialnav\">\n" +
                    "\t<a href=\"/films/5812\"><img src=\"/style/img/sleft.png\" loading=\"lazy\"  alt=\"\" />Предыдущий</a>\n" +
                    "    <a href=\"/films/5814\">Следующий<img src=\"/style/img/sright.png\" loading=\"lazy\"  alt=\"\" /></a></div><hr/><ul class=\"tlsiconkoi\"><li><a href=\"/add_in_coll/films-5813\" onclick=\"return confirm('Добавить в коллекцию?');\"><img src=\"/style/img/in_collection.png\" loading=\"lazy\"  alt=\"\" /> В коллекцию</a></li>\n" +
                    "<li><a href=\"/films/v/5813\"><img src=\"/style/img/podpiska.png\" loading=\"lazy\"  alt=\"\" />Подписка на обновление качества</a></li>\n" +
                    "<li><a href=\"/films/comm/5813\"><img src=\"/style/img/koment.png\" loading=\"lazy\"  alt=\"\" />Комментарии <span class=\"cb\">47</span></a></li></ul>\n" +
                    "</div> \n" +
                    "<div class=\"clear\"></div></div><h1 class=\"blc2\"><span class=\"acat2\"><img src=\"/style/img/online.png\" loading=\"lazy\"  alt=\"\" />Смотреть онлайн:</span></h1><div class=\"blms\"><script src=\"/films/player/hls.v1.29.js\"></script>\n" +
                    "<script src=\"/films/player/anwap.v18.10.js\"></script><ul class=\"tabs\">\n" +
                    "           \t<li id=\"kino\" class=\"current\">Фильм</li>\n" +
                    "\t\t</ul>\n" +
                    "        <div class=\"onlinefilm\">\n" +
                    "        <div class=\"player\" id=\"videoplayer\" style=\"max-width:1024px;\"></div>\n" +
                    "\t\t<script>\n" +
                    "\t\tvar player = new Playerjs(\"#2eyJpZCI6InZpZGVvcGxheWVyIiwgImR1cmF0aW9uIjoiNTYwOCIsICJ//WXQ2cmpGZA==1cmwiOiJodHRwczovL21pLmFud2FwLnR1YmUvZmlsbXMvNTgxMyIsI//RmtpVTdoRw==CJmaWxlIjoiaHR0cHM6Ly96NS5hbndhcC5iZS9vbi92eElmd2piOUJHeGpTLUtFZU83V1FnLzE2NzQwODk4MjcvSXNob2RueWpfa29kLTU4MTNfKGFud2FwLm9yZykubXA0IiwicG9zdGVyIjoiaHR0cHM6Ly9taS5hbndhcC5//U//RXJTdzNBc2k=lRkM1M2NUZn0dWJlL2ZpbG1zL3NjcmVlbi81ODEzLmpwZyIsInN0YXJ0IjoiNjAiLCJwcmVyb2xsIjoiaWQ6dmFzdDU1ODgifQ==\");\n" +
                    "\t\tfunction hgT3fdre(url){ return \"https://code.moviead55.ru/ovpaid.php?v=e7d3af8169f004b81879fab1d5315253&it=1&tq=2&position=pre&dd=1&itout=15\"; }\n" +
                    "\t\t\n" +
                    "\t\t</script></div><div class=\"clear\"></div><ul class=\"tlsiconkoi\"><li><a href=\"/films/5813/off\"><img src=\"/style/img/play-blue.png\" loading=\"lazy\"  alt=\"\" />Скрыть плеер</a></li></ul></div>\n" +
                    " <div class=\"blc\"><span class=\"acat\"><img src=\"/style/img/download.png\" loading=\"lazy\"  alt=\"\" />Трейлер:</span></div>\n" +
                    "<div class=\"blm\">\n" +
                    "<ul class=\"tl\">\n" +
                    "<li><a href=\"/films/load/tr/377f8/1/5813\" class=\"butt\">Искать трейлер</a></li></ul></div><h1 class=\"blc2\" id=\"down\"><span class=\"acat2\"><img src=\"/style/img/raz.png\" loading=\"lazy\"  alt=\"\" />Скачать фильм:</span></h1><div class=\"blms\"><ul class=\"tl2\"><li><a href=\"/films/load/377f8/1/5813\">Скачать 3GP 176x144 <span class=\"black\">118.53мб.</span></a></li><li><a href=\"/films/load/377f8/2/5813\">Скачать MP4 320x240 <span class=\"black\">221.95мб.</span></a></li><li><a href=\"/films/load/377f8/3/5813\">Скачать MP4 720x400 <span class=\"black\">473.06мб.</span></a></li></ul></div><div class=\"blc\"><span class=\"acat\"><img src=\"/style/img/download.png\" loading=\"lazy\"  alt=\"\" />Предпросмотр (1 мин.) :</span></div><div class=\"blm\">\n" +
                    "<ul class=\"tlsiconkoi\">\n" +
                    "<li class=\"butt\" onclick=\"view('t1'); return false\"><img src=\"/style/img/sampl.png\" loading=\"lazy\"  alt=\"\" /> Скачать сэмпл</li></ul>\n" +
                    "<div id=\"t1\" class=\"spoiler_body\"><ul class=\"tlsiconkoi\"><li><a href=\"/films/load/s/377f8/1/5813\"><img src=\"/style/img/arrowdown.png\" loading=\"lazy\"  alt=\"\" />Скачать 3GP 176x144 <span class=\"black\">1.26мб.</span></a></li><li><a href=\"/films/load/s/377f8/2/5813\"><img src=\"/style/img/arrowdown.png\" loading=\"lazy\"  alt=\"\" />Скачать MP4 320x240 <span class=\"black\">2.41мб.</span></a></li><li><a href=\"/films/load/s/377f8/3/5813\"><img src=\"/style/img/arrowdown.png\" loading=\"lazy\"  alt=\"\" />Скачать MP4 720x400 <span class=\"black\">5.33мб.</span></a></li></ul></div>\n" +
                    "<hr /><ul class=\"listserial\"><li><a href=\"/films/parts/5813\"><img src=\"/style/img/cut.png\" loading=\"lazy\"  alt=\"\" /> Вырезать отрывок</a></li>\n" +
                    "<li><a href=\"/films/lp/5813\"><img src=\"/style/img/downpart.png\" loading=\"lazy\"  alt=\"\" /> Скачать по частям</a></li></ul>\n" +
                    "<div class=\"clear\"></div>\n" +
                    "</div><div class=\"menuniz\">\n" +
                    "<a href=\"/films/search\"><img src=\"/style/img/newpoisk.png\" loading=\"lazy\"  alt=\"\" />Поиск</a>\n" +
                    "<a href=\"https://mi.anwap.tube\"><img src=\"/style/img/back.png\" loading=\"lazy\"  alt=\"\" />В фильмы</a></div><div class=\"menuniz\"><a href=\"https://m.anwap.love\" id=\"naglav\"><img src=\"/style/img/home.png\" alt=\"\" />На главную</a></div><div class=\"chislo1\"><span>AnWap</span><span id=\"vk\"><a href=\"https://vk.com/anwaporg\">Мы Вконтакте</a></span></div>\n" +
                    "    <footer><div class=\"footban\"><a href=\"https://mobtop.ru/in/23888\"><img src=\"https://mobtop.ru/23888.gif\" alt=\"\"/></a></div><br />\n" +
                    "<div class=\"online\"><a href=\"https://m.anwap.love/users&amp;on\">297</a> <a href=\"https://m.anwap.love/online.php\">3715</a></div></footer><script> (function(m,e,t,r,i,k,a){m[i]=m[i]||function(){(m[i].a=m[i].a||[]).push(arguments)}; m[i].l=1*new Date();k=e.createElement(t),a=e.getElementsByTagName(t)[0],k.async=1,k.src=r,a.parentNode.insertBefore(k,a)}) (window, document, \"script\", \"/js/tm.js\", \"ym\"); ym(20561500, \"init\", { trackLinks:true, accurateTrackBounce:true }); </script><script> if('serviceWorker' in navigator){window.addEventListener('load',function(){navigator.serviceWorker.register('/anwap_sw.js',{ scope:'/'})})}</script><!--0.0188 сек.--></body>\n" +
                    "</html>"
        )
        val selectors = Selectors.pageVideoSelectors
        val url = getVideoUrl(element, selectors)
        assert(url.contains(".mp4"))
    }


}