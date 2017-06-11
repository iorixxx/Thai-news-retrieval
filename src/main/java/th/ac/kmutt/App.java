package th.ac.kmutt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello world!
 */
public class App {

    private final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();


    private static final Logger LOG = LogManager.getLogger(App.class);

    private static final Map<String, String> categorySeedMap =

            Collections.unmodifiableMap(

                    new HashMap<String, String>() {{

                        put("regional", "https://www.dailynews.co.th/regional");
                        put("politics", "https://www.dailynews.co.th/politics");
                        put("foreign", "https://www.dailynews.co.th/foreign");
                        put("crime", "https://www.dailynews.co.th/crime");
                        put("sports", "https://www.dailynews.co.th/sports");
                        put("it", "https://www.dailynews.co.th/it");
                        put("entertainment", "https://www.dailynews.co.th/entertainment");
                        put("economic", "https://www.dailynews.co.th/economic");
                        put("women", "https://www.dailynews.co.th/women");
                        put("agriculture", "https://www.dailynews.co.th/agriculture");
                        put("royalnews", "https://www.dailynews.co.th/royalnews");
                        put("bangkok", "https://www.dailynews.co.th/bangkok");
                        put("education", "https://www.dailynews.co.th/education");

                    }}
            );


    private Map<Integer, String> handleCategory(String seed, String category) {


        Map<Integer, String> discoveredLinks = new HashMap<>(10);

        Document doc;

        try {

            doc = Jsoup.connect(seed).get();
        } catch (IOException ioe) {
            LOG.error("IOException while discovering links from " + seed);
            return discoveredLinks;
        }

        Elements links = doc.select("a[href]");

        for (Element link : links) {

            String url = link.attr("abs:href");

            String startWith = "https://www.dailynews.co.th/" + category;

            if (!url.startsWith(startWith)) continue;

            String part = url.substring(startWith.length());

            if (part.length() < 7) continue;

            if (part.charAt(0) != '/') continue;

            part = part.substring(1);

            int newsID;
            try {
                newsID = Integer.parseInt(part);
            } catch (NumberFormatException nfe) {
                LOG.info("NumberFormatException converting integer " + url);
                continue;
            }

            if (!discoveredLinks.containsKey(newsID))
                discoveredLinks.put(newsID, url);


        }

        return discoveredLinks;
    }


    /**
     * Convert link String to JSoup document.
     *
     * @param link URL
     * @return JSoup Document
     */
    private Document link2Document(String link) {

        Document doc = null;

        try {
            doc = Jsoup.connect(link).get();
        } catch (HttpStatusException e) {
            LOG.warn("HttpStatusException while processing URL : " + link);
            LOG.debug("HttpStatusException while processing URL : " + link, e);
        } catch (org.jsoup.UnsupportedMimeTypeException e) {
            LOG.warn("UnsupportedMimeTypeException while processing URL : " + link);
            LOG.debug("UnsupportedMimeTypeException while processing URL : " + link, e);
        } catch (SocketTimeoutException ste) {
            LOG.warn("SocketTimeoutException while processing URL : " + link);
            LOG.debug("SocketTimeoutException while processing URL : " + link, ste);
        } catch (Exception e) {
            LOG.warn("Any exception while processing URL : " + link);
            LOG.debug("Any exception while processing URL : " + link, e);
        }

        return doc;
    }

    private String text(Document doc) {

        Elements content = doc.select("div[class=entry textbox content-all]");

        if (content != null && content.size() > 0) {
            String text = content.first().text();
            if (text != null && text.length() > 5)
                return text.trim();
        }

        return null;
    }

    private News singleURL(int newsID, String url, String category) {

        Document doc = link2Document(url);

        if (doc == null) {
            return null;
        }

        News news = new News();
        news.id = newsID;
        news.url = url;
        news.category = category;
        news.title = doc.title();


        news.content = text(doc);


        Elements elements = doc.select("meta[name=description]");
        if (elements != null && elements.size() > 0) {
            String desc = elements.first().attr("content");
            if (desc != null && desc.length() > 5)
                news.description = desc;
        }

        elements = doc.select("meta[name=keywords]");
        if (elements != null && elements.size() > 0) {
            String keywords = elements.first().attr("content");
            if (keywords != null && keywords.length() > 5)
                news.keywords = keywords;
        }

        return news;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            LOG.info("interrupted exception while sleeping", ie);
        }
    }

    private void doCrawl(int page) {


        for (Map.Entry<String, String> entry : categorySeedMap.entrySet()) {

            final String category = entry.getKey();
            Path p = Paths.get(".").resolve("data").resolve(category);

            try {
                Files.createDirectories(p);
            } catch (IOException ioe) {
                LOG.error("io exception creating directories", ioe);
            }

            final String seed = page == 0 ? entry.getValue() : entry.getValue() + "?page=" + page;

            sleep(1000);

            Map<Integer, String> discoveredLinks = handleCategory(seed, category);


            LOG.info("seed " + seed + " returned " + discoveredLinks.size() + " many links");

            int old = 0;
            for (Map.Entry<Integer, String> entry1 : discoveredLinks.entrySet()) {

                if (Files.exists(p.resolve(entry1.getKey() + ".json"))) {
                    LOG.debug(p.resolve(entry1.getKey() + ".json").toString() + " has been saved in previous crawls");
                    old++;
                    continue;
                }


                LOG.debug("proceeding with the link " + entry1.getValue());
                sleep(1000);

                News news = singleURL(entry1.getKey(), entry1.getValue(), category);

                if (news == null) continue;

                //   System.out.println(news.url + " " + news.title + " " + news.keywords + " " + news.description);

                String json = gson.toJson(news);

                try {

                    if (Files.notExists(p.resolve(news.id + ".json")))
                        Files.write(p.resolve(news.id + ".json"), json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
                    else
                        LOG.info(p.resolve(news.id + ".json").toString() + " has been saved in previous crawls");
                } catch (IOException ioe) {
                    LOG.error("during saving json file ", ioe);
                }

            }

            LOG.info(discoveredLinks.size() - old + " new links out of " + discoveredLinks.size() + " are processed");
        }

    }

    public static void main(String[] args) {

        App app = new App();

        for (int page = 10; page >= 0; page--)
            app.doCrawl(page);

    }
}
