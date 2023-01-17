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
                    "<meta charset=\"UTF-8\"/><title>Скачать фильм Жил-был пёс    1982г на телефон или планшет бесплатно</title><meta name=\"keywords\" content=\"1982, скачать по частям, смотреть онлайн, скачать на android, скачать кино, скачать 3gp mp4, СССР, мультфильм, короткометражка, комедия, семейный\" /><meta name=\"description\" content=\"Жил-был пёс    - Жил-был пёс. Верно служил, но выгнали его по старости. И решил он повеситься, да повстречал в лесу такого же старого волка&#133;...\" />\n" +
                    "<meta property=\"og:title\" content=\"Жил-был пёс    (1982) - скачать для планшета или телефона\" />\n" +
                    "<meta property=\"og:url\" content=\"https://mi.anwap.tube/films/9220\" />\n" +
                    "<meta property=\"og:image\" content=\"https://mi.anwap.tube/films/kadrs/9220.jpg\" />\n" +
                    "<meta property=\"og:desc\" content=\"Жил-был пёс. Верно служил, но&nbsp;выгнали его&nbsp;по старости. И&nbsp;решил он&nbsp;повеситься, да&nbsp;повстречал в&nbsp;лесу такого же&nbsp;старого волка&#133;\" />\n" +
                    "<meta property=\"og:type\" content=\"video.movie\" />\n" +
                    "<meta property=\"og:duration\" content=\"600\" />\n" +
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
                    ".sfxpeqtc,.pnlqdsqn{ margin:0 2px 2px; padding: 2px;border-radius:0 0 8px 8px;border-bottom: 3px solid #FFFFFF;  border-top: 1px solid #FFFFFF;\n" +
                    "background: url(/style/img/hot.png) no-repeat right bottom, url(/style/img/hh.png) repeat-x top #c2e5fe;\n" +
                    "min-height: 62px}\n" +
                    ".sfxpeqtc img,.pnlqdsqn img {border-radius: 8px;box-shadow: 0 1px 2px #000; float: left;}\n" +
                    ".sfxpeqtc a,.pnlqdsqn a{ color:#000; text-shadow: 0 1px 2px  #fff}\n" +
                    "</style><link rel=\"canonical\" href=\"https://mi.anwap.tube/films/9220\" /></head><body><div class=\"logo\"><a href=\"https://m.anwap.love\" title=\"Anwap\"></a></div>\n" +
                    "<div class=\"chislo\"><span><img src=\"/smiles/salut3_545.gif\" height=\"15\" alt=\"\" />Градиент позитива<img src=\"/smiles/salut3_545.gif\" height=\"15\" alt=\"\" /></span></div><div style=\"text-align: center;\"><div class=\"save\"><div class=\"zag2\"><a href=\"https://m.anwap.love/enter\">Вход</a> |<a href=\"https://m.anwap.love/reg\">Регистрация</a></div></div></div><div class=\"ball\"><h1 class=\"blc\"><span class=\"acat\"><img src=\"/style/img/polez.png\" alt=\"\" />Жил-был пёс   </span></h1></div><div class=\"blm\"><script>\n" +
                    "function view(n) { style = document.getElementById(n).style; style.display = (style.display == 'block') ? 'none' : 'block'; }\n" +
                    "</script>\n" +
                    "<div class=\"filmopis screen\">\n" +
                    "<img src=\"https://mi.anwap.tube/films/screen/9220.jpg\" loading=\"lazy\"  alt=\"Жил-был пёс   \" title=\"Жил-был пёс    скачать на телефон бесплатно\" class=\"filmscreen\" /> <div class=\"rating\" id=\"rating\">\n" +
                    "<span class=\"knopka like\" onclick=\"return alert('Для голосования необходимо зарегистрироваться на сайте или войти в свой аккаунт, если уже зарегистрированны!');\">189</span><span class=\"knopka dislike\" onclick=\"return alert('Для голосования необходимо зарегистрироваться на сайте или войти в свой аккаунт, если уже зарегистрированны!');\">3</span>\n" +
                    " </div>\n" +
                    "\n" +
                    "\t<script>\n" +
                    "\tfunction Rate(id, uid, hash, type) {var xhr = new XMLHttpRequest();xhr.open(\"POST\", \"https://mi.anwap.tube/js/ajax/rating.php\", false);xhr.setRequestHeader(\"Content-Type\", \"application/x-www-form-urlencoded\");xhr.send(\"act=rating&vid=films&pid=\"+id+\"&uid=\"+uid+\"&hash=\"+hash+\"&type=\"+type);\n" +
                    "    if (xhr.status == 200) {document.getElementById(\"rating\").innerHTML = xhr.responseText;}}\n" +
                    "\t</script></div><div class=\"filmopis screen2\">\n" +
                    "\t\t<table>\n" +
                    "\t\t<tr><td>Обновлен:</td><td>2013.11.16 20:19</td></tr>\n" +
                    "\t\t \n" +
                    "\t\t \n" +
                    "\t\t<tr><td>Год:</td><td><a href=\"/films/god-1982\">1982</a></td></tr> \n" +
                    "        <tr><td>Качество:</td><td><a style=\"color: #008000;\" href=\"/films/formats\">DVDRip(отличное)</a></td></tr>\t\t\n" +
                    "\t\t \n" +
                    "\t\t<tr><td>Время:</td><td>00:10:00</td></tr>\n" +
                    "\t\t<tr><td>Страна:</td><td><a href=\"/films/c1\">СССР</a></td></tr> \n" +
                    "\t\t<tr><td>Жанр:</td><td><a href=\"/films/r22\">мультфильм</a>, <a href=\"/films/r15\">короткометражка</a>, <a href=\"/films/r14\">комедия</a>, <a href=\"/films/r26\">семейный</a></td></tr>\n" +
                    "\t\t<tr><td>Рейтинг:</td><td><img src=\"/style/img/imdb.png\" loading=\"lazy\"  alt=\"\" /> 8.4/10 | <img src=\"/style/img/kp.png\" loading=\"lazy\"  alt=\"\" /> 9.196/10</td></tr> \n" +
                    "\t\t<tr><td>Режиссер:</td><td>Эдуард Назаров</td></tr> \n" +
                    "\t\t<tr><td>Актеры:</td><td>Георгий Бурков,Армен Джигарханян,Эдуард Назаров,</td></tr>\n" +
                    "\t\t<tr><td>Скачали:</td><td>35009 раз (20 сегодня)</td></tr> \t\t\n" +
                    "\t\t</table></div><div class=\"clear\"></div>\n" +
                    "\t\t<div class=\"filmopis screen3\"><p>Жил-был пёс. Верно служил, но&nbsp;выгнали его&nbsp;по старости. И&nbsp;решил он&nbsp;повеситься, да&nbsp;повстречал в&nbsp;лесу такого же&nbsp;старого волка&#133;</p><hr/><div class=\"serialnav\">\n" +
                    "\t<a href=\"/films/9219\"><img src=\"/style/img/sleft.png\" loading=\"lazy\"  alt=\"\" />Предыдущий</a>\n" +
                    "    <a href=\"/films/9221\">Следующий<img src=\"/style/img/sright.png\" loading=\"lazy\"  alt=\"\" /></a></div><hr/><ul class=\"tlsiconkoi\"><li><a href=\"/add_in_coll/films-9220\" onclick=\"return confirm('Добавить в коллекцию?');\"><img src=\"/style/img/in_collection.png\" loading=\"lazy\"  alt=\"\" /> В коллекцию</a></li>\n" +
                    "<li><a href=\"/films/v/9220\"><img src=\"/style/img/podpiska.png\" loading=\"lazy\"  alt=\"\" />Подписка на обновление качества</a></li>\n" +
                    "<li><a href=\"/films/comm/9220\"><img src=\"/style/img/koment.png\" loading=\"lazy\"  alt=\"\" />Комментарии <span class=\"cb\">21</span></a></li></ul>\n" +
                    "</div> \n" +
                    "<div class=\"clear\"></div></div><h1 class=\"blc2\"><span class=\"acat2\"><img src=\"/style/img/online.png\" loading=\"lazy\"  alt=\"\" />Смотреть онлайн:</span></h1><div class=\"blms\"><script src=\"/films/player/hls.v1.29.js\"></script>\n" +
                    "<script src=\"/films/player/anwap.v18.10.js\"></script><ul class=\"tabs\">\n" +
                    "           \t<li id=\"kino\" class=\"current\" onclick=\"return changevideo('kino');\">Фильм</li>\n" +
                    "\t\t\t<li id=\"kinohd\" class=\"\" onclick=\"return changevideo('kinohd');\">Фильм HD</li>\n" +
                    "\t\t\t<li id=\"trail\" class=\"\" onclick=\"return changevideo('trail');\">Трейлер</li>\n" +
                    "\t\t</ul>\n" +
                    "        <div class=\"onlinefilm\">\n" +
                    "        <div class=\"player\" id=\"videoplayer\" style=\"max-width:1024px;\"></div>\n" +
                    "\t\t<script>\n" +
                    "\t\tfunction changevideo(ids) {\n" +
                    "\t\tvar id = document.getElementById(ids);\n" +
                    "\t\tvar kino = document.getElementById(\"kino\");\n" +
                    "\t\tvar kinohd = document.getElementById(\"kinohd\");\n" +
                    "\t\tvar trail = document.getElementById(\"trail\");\n" +
                    "\t\tif (ids == \"kino\") { \n" +
                    "\t\t       trail.setAttribute(\"class\", \"\"); \n" +
                    "\t\t       kino.setAttribute(\"class\", \"current\");\n" +
                    "               var player = new Playerjs(\"#2eyJpZCI6InZpZGVvcGxheWVyI//WXQ2cmpGZA==iwgImR1cmF0aW//RXJTdzNBc2k=9uIjoiNjAwIiwgInVybCI6Imh0dHBzOi8vbWkuYW53YXAudHViZS9maWxtcy85MjIwIiwgImZpbGUiOiJodHRwczovL28xLmFud2FwLmJlL29obHMvekJsdUdPOVpKRk1pankwaGIxakxtdy8xNjc0MDkyNzIyL1pIaWwtYnlsX3B5b3MtOTIyMF8oYW53YXAub3JnKS5tM3U4IG9yIGh0dHBzOi8vbzEuYW53YXAuYmUvb24vekJsdUdPOVpKRk1pankwaGIxakxtdy8xNjc0MDkyNzIyL1pIaWwtYnlsX3B5b3MtOTIyMF8oYW53YXAub3JnKS5tcDQgb3IgaHR0cHM6Ly96NS5hbndhcC5iZS9vbi96Qmx1R085WkpGTWlqeTBoYjFqTG13LzE2NzQwOTI3MjIvWkhpbC1ieWxfcHlvcy05MjIwXyhhbndhcC5vcmcpLm1wNCIsInBvc3RlciI//RmtpVTdoRw==6Imh0dHBzOi8vbWkuYW53YXAudHViZS9maWxtcy9rYWRycy85MjIwLmpwZy//UlRkM1M2NUZnIsInN0YXJ0IjoiNjAiLCJwcmVyb2xsIjoiaWQ6dmFzdDU1ODgifQ==\");\n" +
                    "               kinohd.setAttribute(\"class\", \"\");\t   \t       \n" +
                    "\t\t   } else if (ids == \"kinohd\") { \n" +
                    "\t\t       trail.setAttribute(\"class\", \"\");\n" +
                    "\t\t\t   kino.setAttribute(\"class\", \"\"); \n" +
                    "\t\t       kinohd.setAttribute(\"class\", \"current\");\n" +
                    "\t\t\t   document.getElementById(\"videoplayer\").innerHTML = '<div style=\"position:relative;width:100%;display:inline-block;max-width:1024px;\"><iframe loading=\"lazy\" src=\"https://api.delivembed.cc/embed/movie/2839\" title=\"Жил-был пёс   \" allow=\"autoplay *\" allowfullscreen=\"\"  webkitallowfullscreen=\"\" mozallowfullscreen=\"\" oallowfullscreen=\"\" msallowfullscreen=\"\" width=\"100%\" height=\"100%\" style=\"border:none;position:absolute;top:0;left:0;width:100%;height:100%;\"></iframe><div style=\"padding-top:56.25%;\"></div></div>';\n" +
                    "           } else {\n" +
                    "               var player = new Playerjs(\"#2eyJpZC//UlRkM1M2NUZnI6InZpZGVvcGxheWVyIiwgImR1cmF0aW9uIjoiMTM0IiwgInVybCI6Imh0dHBzOi8vbWkuYW53YXAudHViZS9maWxtcy8xNzk2MSIsI//RmtpVTdoRw==CJmaWxlIjoiaHR0cHM6Ly90ci5hbnd//WXQ2cmpGZA==hcC5iZS9tcDRfYmlnLzkyMjAubXA0IiwicG9zdGVyIjoiaHR0cHM6Ly9taS5hbndhcC50dWJlL2ZpbG1zL2thZHJzLzkyMjAuanBnI//RXJTdzNBc2k=iwicHJlcm9sbCI6ImlkOnZhc3Q1NTg4In0=\");\t\t   \n" +
                    "\t\t       trail.setAttribute(\"class\", \"current\"); \n" +
                    "\t\t       kino.setAttribute(\"class\", \"\");\n" +
                    "\t\t\t   kinohd.setAttribute(\"class\", \"\");\n" +
                    "\t\t   }\n" +
                    "        }\n" +
                    "\t\tchangevideo('kinohd');\n" +
                    "\t\t</script></div><div class=\"clear\"></div><ul class=\"tlsiconkoi\"><li><a href=\"/films/9220/off\"><img src=\"/style/img/play-blue.png\" loading=\"lazy\"  alt=\"\" />Скрыть плеер</a></li></ul></div>\n" +
                    " <div class=\"blc\"><span class=\"acat\"><img src=\"/style/img/download.png\" loading=\"lazy\"  alt=\"\" />Трейлер:</span></div>\n" +
                    "<div class=\"blm\">\n" +
                    "<ul class=\"tl\">\n" +
                    "<li><a href=\"/films/load/tr/ed7a4/1/9220\" class=\"butt\">Скачать MP4 <span class=\"black\">30.96мб.</span></a></li></ul></div><h1 class=\"blc2\" id=\"down\"><span class=\"acat2\"><img src=\"/style/img/raz.png\" loading=\"lazy\"  alt=\"\" />Скачать фильм:</span></h1><div class=\"blms\"><ul class=\"tl2\"><li><a href=\"/films/load/ed7a4/1/9220\">Скачать 3GP 176x144 <span class=\"black\">10.93мб.</span></a></li><li><a href=\"/films/load/ed7a4/2/9220\">Скачать MP4 320x240 <span class=\"black\">21.8мб.</span></a></li><li><a href=\"/films/load/ed7a4/3/9220\">Скачать MP4 640x480 <span class=\"black\">34.06мб.</span></a></li></ul></div><div class=\"blc\"><span class=\"acat\"><img src=\"/style/img/download.png\" loading=\"lazy\"  alt=\"\" />Предпросмотр (1 мин.) :</span></div><div class=\"blm\">\n" +
                    "<ul class=\"tlsiconkoi\">\n" +
                    "<li class=\"butt\" onclick=\"view('t1'); return false\"><img src=\"/style/img/sampl.png\" loading=\"lazy\"  alt=\"\" /> Скачать сэмпл</li></ul>\n" +
                    "<div id=\"t1\" class=\"spoiler_body\"><ul class=\"tlsiconkoi\"><li><a href=\"/films/load/s/ed7a4/1/9220\"><img src=\"/style/img/arrowdown.png\" loading=\"lazy\"  alt=\"\" />Скачать 3GP 176x144 <span class=\"black\">1.15мб.</span></a></li><li><a href=\"/films/load/s/ed7a4/2/9220\"><img src=\"/style/img/arrowdown.png\" loading=\"lazy\"  alt=\"\" />Скачать MP4 320x240 <span class=\"black\">2.2мб.</span></a></li><li><a href=\"/films/load/s/ed7a4/3/9220\"><img src=\"/style/img/arrowdown.png\" loading=\"lazy\"  alt=\"\" />Скачать MP4 640x480 <span class=\"black\">4.14мб.</span></a></li></ul></div>\n" +
                    "<hr /><ul class=\"listserial\"><li><a href=\"/films/parts/9220\"><img src=\"/style/img/cut.png\" loading=\"lazy\"  alt=\"\" /> Вырезать отрывок</a></li>\n" +
                    "<li><a href=\"/films/lp/9220\"><img src=\"/style/img/downpart.png\" loading=\"lazy\"  alt=\"\" /> Скачать по частям</a></li></ul>\n" +
                    "<div class=\"clear\"></div>\n" +
                    "</div><div class=\"menuniz\">\n" +
                    "<a href=\"/films/search\"><img src=\"/style/img/newpoisk.png\" loading=\"lazy\"  alt=\"\" />Поиск</a>\n" +
                    "<a href=\"https://mi.anwap.tube\"><img src=\"/style/img/back.png\" loading=\"lazy\"  alt=\"\" />В фильмы</a></div><div class=\"menuniz\"><a href=\"https://m.anwap.love\" id=\"naglav\"><img src=\"/style/img/home.png\" alt=\"\" />На главную</a></div><div class=\"chislo1\"><span>AnWap</span><span id=\"vk\"><a href=\"https://vk.com/anwaporg\">Мы Вконтакте</a></span></div>\n" +
                    "    <footer><div class=\"footban\"><a href=\"https://mobtop.ru/in/23888\"><img src=\"https://mobtop.ru/23888.gif\" alt=\"\"/></a></div><br />\n" +
                    "<div class=\"online\"><a href=\"https://m.anwap.love/users&amp;on\">274</a> <a href=\"https://m.anwap.love/online.php\">3915</a></div></footer><script> (function(m,e,t,r,i,k,a){m[i]=m[i]||function(){(m[i].a=m[i].a||[]).push(arguments)}; m[i].l=1*new Date();k=e.createElement(t),a=e.getElementsByTagName(t)[0],k.async=1,k.src=r,a.parentNode.insertBefore(k,a)}) (window, document, \"script\", \"/js/tm.js\", \"ym\"); ym(20561500, \"init\", { trackLinks:true, accurateTrackBounce:true }); </script><script> if('serviceWorker' in navigator){window.addEventListener('load',function(){navigator.serviceWorker.register('/anwap_sw.js',{ scope:'/'})})}</script><!--0.0325 сек.--></body>\n" +
                    "</html>"
        )
        val selectors = Selectors.pageVideoSelectors
        val url = getVideoUrl(element, selectors)
        assert(url.contains(".mp4") || url.contains(".m3u8"))
    }


}