package com.airbnb.lottie;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.annotation.RestrictTo;
import android.support.v4.util.LongSparseArray;
import android.support.v4.util.SparseArrayCompat;
import android.util.JsonReader;
import android.util.Log;

import com.airbnb.lottie.model.FileCompositionLoader;
import com.airbnb.lottie.model.Font;
import com.airbnb.lottie.model.FontCharacter;
import com.airbnb.lottie.model.JsonCompositionLoader;
import com.airbnb.lottie.model.layer.Layer;
import com.airbnb.lottie.utils.JsonUtils;
import com.airbnb.lottie.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.airbnb.lottie.utils.Utils.closeQuietly;

/**
 * After Effects/Bodymovin composition model. This is the serialized model from which the
 * animation will be created.
 * It can be used with a {@link com.airbnb.lottie.LottieAnimationView} or
 * {@link com.airbnb.lottie.LottieDrawable}.
 */
public class LottieComposition {

  private final Map<String, List<Layer>> precomps = new HashMap<>();
  private final Map<String, LottieImageAsset> images = new HashMap<>();
  /** Map of font names to fonts */
  private final Map<String, Font> fonts = new HashMap<>();
  private final SparseArrayCompat<FontCharacter> characters = new SparseArrayCompat<>();
  private final LongSparseArray<Layer> layerMap = new LongSparseArray<>();
  private final List<Layer> layers = new ArrayList<>();
  // This is stored as a set to avoid duplicates.
  private final HashSet<String> warnings = new HashSet<>();
  private final PerformanceTracker performanceTracker = new PerformanceTracker();
  private final Rect bounds;
  private final float startFrame;
  private final float endFrame;
  private final float frameRate;
  private final float dpScale;
  /* Bodymovin version */
  private final int majorVersion;
  private final int minorVersion;
  private final int patchVersion;

  private LottieComposition(Rect bounds, float startFrame, float endFrame, float frameRate,
      float dpScale, int major, int minor, int patch) {
    this.bounds = bounds;
    this.startFrame = startFrame;
    this.endFrame = endFrame;
    this.frameRate = frameRate;
    this.dpScale = dpScale;
    this.majorVersion = major;
    this.minorVersion = minor;
    this.patchVersion = patch;
    if (!Utils.isAtLeastVersion(this, 4, 5, 0)) {
      addWarning("Lottie only supports bodymovin >= 4.5.0");
    }
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public void addWarning(String warning) {
    Log.w(L.TAG, warning);
    warnings.add(warning);
  }

  public ArrayList<String> getWarnings() {
    return new ArrayList<>(Arrays.asList(warnings.toArray(new String[warnings.size()])));
  }

  public void setPerformanceTrackingEnabled(boolean enabled) {
    performanceTracker.setEnabled(enabled);
  }

  public PerformanceTracker getPerformanceTracker() {
    return performanceTracker;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public Layer layerModelForId(long id) {
    return layerMap.get(id);
  }

  @SuppressWarnings("WeakerAccess") public Rect getBounds() {
    return bounds;
  }

  @SuppressWarnings("WeakerAccess") public float getDuration() {
    float frameDuration = endFrame - startFrame;
    return (long) (frameDuration / frameRate * 1000);
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public int getMajorVersion() {
    return majorVersion;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public int getMinorVersion() {
    return minorVersion;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public int getPatchVersion() {
    return patchVersion;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public float getStartFrame() {
    return startFrame;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public float getEndFrame() {
    return endFrame;
  }

  public List<Layer> getLayers() {
    return layers;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  @Nullable
  public List<Layer> getPrecomps(String id) {
    return precomps.get(id);
  }

  public SparseArrayCompat<FontCharacter> getCharacters() {
    return characters;
  }

  public Map<String, Font> getFonts() {
    return fonts;
  }

  public boolean hasImages() {
    return !images.isEmpty();
  }

  @SuppressWarnings("WeakerAccess") public Map<String, LottieImageAsset> getImages() {
    return images;
  }

  public float getDurationFrames() {
    return getDuration() * frameRate / 1000f;
  }


  public float getDpScale() {
    return dpScale;
  }

  @Override public String toString() {
    final StringBuilder sb = new StringBuilder("LottieComposition:\n");
    for (Layer layer : layers) {
      sb.append(layer.toString("\t"));
    }
    return sb.toString();
  }

  public static class Factory {
    private Factory() {
    }

    /**
     * Loads a composition from a file stored in /assets.
     */
    public static Cancellable fromAssetFileName(Context context, String fileName,
        OnCompositionLoadedListener loadedListener) {
      InputStream stream;
      try {
        stream = context.getAssets().open(fileName);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to find file " + fileName, e);
      }
      return fromInputStream(context, stream, loadedListener);
    }

    /**
     * Loads a composition from a file stored in res/raw.
     */
    public static Cancellable fromRawFile(Context context, @RawRes int resId,
        OnCompositionLoadedListener loadedListener) {
      return fromInputStream(context, context.getResources().openRawResource(resId), loadedListener);
    }

    /**
     * Loads a composition from an arbitrary input stream.
     * <p>
     * ex: fromInputStream(context, new FileInputStream(filePath), (composition) -> {});
     */
    public static Cancellable fromInputStream(Context context, InputStream stream,
        OnCompositionLoadedListener loadedListener) {
      FileCompositionLoader loader =
          new FileCompositionLoader(context.getResources(), loadedListener);
      loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, stream);
      return loader;
    }

    @SuppressWarnings("WeakerAccess")
    public static LottieComposition fromFileSync(Context context, String fileName) {
      InputStream stream;
      try {
        stream = context.getAssets().open(fileName);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to find file " + fileName, e);
      }
      return fromInputStream(context.getResources(), stream);
    }

    /**
     * Loads a composition from a raw json object. This is useful for animations loaded from the
     * network.
     */
    public static Cancellable fromJson(Resources res, JSONObject json,
        OnCompositionLoadedListener loadedListener) {
      JsonCompositionLoader loader = new JsonCompositionLoader(res, loadedListener);
      loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, json);
      return loader;
    }

    @Nullable
    public static LottieComposition fromInputStream(Resources res, InputStream stream) {
      try {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder total = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          total.append(line);
        }
        JSONObject jsonObject = new JSONObject(total.toString());
        return fromJsonSync(res, jsonObject);
      } catch (IOException e) {
        Log.e(L.TAG, "Failed to load composition.",
            new IllegalStateException("Unable to find file.", e));
      } catch (JSONException e) {
        Log.e(L.TAG, "Failed to load composition.",
            new IllegalStateException("Unable to load JSON.", e));
      } finally {
        closeQuietly(stream);
      }
      return null;
    }

    public static LottieComposition fromJsonSync(Resources res, JSONObject json) {
      try {
        return fromJsonSyncInternal(res, JsonUtils.jsonToReader(json));
      } catch (IOException e) {
        throw new IllegalArgumentException("Unable to parse json", e);
      }
    }

    public static LottieComposition fromJsonSyncInternal(Resources res, JsonReader reader)
        throws IOException{
      // TODO: use system resources to not need context.
      float scale = res.getDisplayMetrics().density;
      int width = 0;
      int height = 0;
      float startFrame = 0;
      float endFrame = 0;
      float frameRate = 0;
      int major = 0;
      int minor = 0;
      int patch = 0;

      LottieComposition composition = null;

      List<Layer> layers = new ArrayList<>();
      LongSparseArray<Layer> layerMap = new LongSparseArray<>();
      List<String> warnings = new ArrayList<>();

      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "w":
            width = reader.nextInt();
            break;
          case "h":
            height = reader.nextInt();
            break;
          case "ip":
            startFrame = (float) reader.nextDouble();
            break;
          case "op":
            endFrame = (float) reader.nextDouble();
            break;
          case "fr":
            frameRate = (float) reader.nextDouble();
            break;
          case "v":
            String version = reader.nextString();
            String[] versions = version.split("\\.");
            major = Integer.parseInt(versions[0]);
            minor = Integer.parseInt(versions[1]);
            patch = Integer.parseInt(versions[2]);
            break;
          case "layers":
            int scaledWidth = (int) (width * scale);
            int scaledHeight = (int) (height * scale);
            Rect bounds = new Rect(0, 0, scaledWidth, scaledHeight);
            composition = new LottieComposition(
                bounds, startFrame, endFrame, frameRate, scale, major, minor, patch);

            parseLayers(reader, composition);
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();



      // JSONArray assetsJson = json.optJSONArray("assets");
      // parseImages(assetsJson, composition);
      // parsePrecomps(assetsJson, composition);
      // parseFonts(json.optJSONObject("fonts"), composition);
      // parseChars(json.optJSONArray("chars"), composition);
      return composition;
    }

    private static void parseLayers(JsonReader reader,
        List<Layer> layers, LongSparseArray<Layer> layerMap, List<String> warnings)
        throws IOException {
      int imageCount = 0;
      reader.beginArray();
      while (reader.hasNext()) {
        Layer layer = Layer.Factory.newInstance(reader, composition);
        if (layer.getLayerType() == Layer.LayerType.Image) {
          imageCount++;
        }
        addLayer(layers, layerMap, layer);

        if (imageCount > 4) {
          warnings.add("You have " + imageCount + " images. Lottie should primarily be " +
              "used with shapes. If you are using Adobe Illustrator, convert the Illustrator layers" +
              " to shape layers.");
        }
      }
      reader.endArray();
    }

    private static void parsePrecomps(
        @Nullable JSONArray assetsJson, LottieComposition composition) throws IOException {
      if (assetsJson == null) {
        return;
      }
      JsonReader reader = JsonUtils.jsonToReader(assetsJson);
      reader.beginArray();
      while (reader.hasNext()) {
        List<Layer> layers = new ArrayList<>();
        LongSparseArray<Layer> layerMap = new LongSparseArray<>();
        String id = null;
        reader.beginObject();
        while (reader.hasNext()) {
          switch (reader.nextName()) {
            case "layers":
              reader.beginArray();
              while (reader.hasNext()) {
                Layer layer = Layer.Factory.newInstance(reader, composition);
                layerMap.put(layer.getId(), layer);
                layers.add(layer);
              }
              reader.endArray();
              break;
            case "id":
              id = reader.nextString();
              break;
            default:
              reader.skipValue();
          }
        }
        reader.endObject();
        composition.precomps.put(id, layers);
      }
      reader.endArray();
    }

    private static void parseImages(
        @Nullable JSONArray assetsJson, LottieComposition composition) {
      if (assetsJson == null) {
        return;
      }
      int length = assetsJson.length();
      for (int i = 0; i < length; i++) {
        JSONObject assetJson = assetsJson.optJSONObject(i);
        if (!assetJson.has("p")) {
          continue;
        }
        LottieImageAsset image = LottieImageAsset.Factory.newInstance(assetJson);
        composition.images.put(image.getId(), image);
      }
    }

    private static void parseFonts(@Nullable JSONObject fonts, LottieComposition composition) {
      if (fonts == null) {
        return;
      }
      JSONArray fontsList = fonts.optJSONArray("list");
      if (fontsList == null) {
        return;
      }
      int length = fontsList.length();
      for (int i = 0; i < length; i++) {
        Font font = Font.Factory.newInstance(fontsList.optJSONObject(i));
        composition.fonts.put(font.getName(), font);
      }
    }

    private static void parseChars(@Nullable JSONArray charsJson, LottieComposition composition)
        throws IOException {
      if (charsJson == null) {
        return;
      }

      int length = charsJson.length();
      for (int i = 0; i < length; i++) {
        FontCharacter character =
            FontCharacter.Factory.newInstance(charsJson.optJSONObject(i), composition);
        composition.characters.put(character.hashCode(), character);
      }
    }

    private static void addLayer(List<Layer> layers, LongSparseArray<Layer> layerMap, Layer layer) {
      layers.add(layer);
      layerMap.put(layer.getId(), layer);
    }
  }
}
