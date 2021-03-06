/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.pay.android.openiab;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import com.badlogic.gdx.pay.Information;
import com.badlogic.gdx.pay.Offer;
import com.badlogic.gdx.pay.OfferType;
import com.badlogic.gdx.pay.PurchaseManager;
import com.badlogic.gdx.pay.PurchaseManagerConfig;
import com.badlogic.gdx.pay.PurchaseObserver;
import com.badlogic.gdx.pay.Transaction;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.SkuManager;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.Purchase;
import org.onepf.oms.appstore.googleUtils.SkuDetails;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The purchase manager implementation for Android via <a href="http://www.onepf.org/openiab">OpenIAB</a>. Supported stores
 * include:
 * <ul>
 * <li>Google Play
 * <li>Amazon
 * <li>Samsung Apps
 * <li>Nokia
 * <li>Open Store
 * <li>SlideME
 * <li>Aptoide
 * </ul>
 * Please note that Fortumo is not included.
 * <p>
 * Include the gdx-pay-android-openiab.jar for this to work (plus gdx-pay-android.jar). Also update the "uses-permission" settings
 * in AndroidManifest.xml and your proguard settings.
 * 
 * @author noblemaster */
public class PurchaseManagerAndroidOpenIAB implements PurchaseManager {

	/** Debug tag for logging. */
	private static final String TAG = "GdxPay/OpenIAB";

	/** Our Android activity. */
	private Activity activity;
	/** The request code to use for onActivityResult (arbitrary chosen). */
	private int requestCode;

	/** The registered observer. */
	PurchaseObserver observer;
	/** The configuration. */
	PurchaseManagerConfig config;

	/** Our OpenIAB helper class through which we get access to the various markets. */
	OpenIabHelper helper;
	/** The inventory with all the prices/details. */
	Inventory inventory;

	boolean autoFetchInformation;

	public PurchaseManagerAndroidOpenIAB (Activity activity, int requestCode) {
		this.activity = activity;

		// the request code for onActivityResult
		this.requestCode = requestCode;
	}

	@Override
	public String storeName () {
		// return the correct store name
		if (helper != null) {
			return storeNameFromOpenIAB(helper.getConnectedAppstoreName());
		} else {
			// create temporary helper to retrieve store name (not very clean though!)
			return null;
		}
	}

	private String storeNameFromOpenIAB (String storeNameOpenIAB) {
		if (storeNameOpenIAB == null) {
			return null;
		} else {
			if (storeNameOpenIAB.equals(OpenIabHelper.NAME_GOOGLE)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_GOOGLE;
			} else if (storeNameOpenIAB.equals(OpenIabHelper.NAME_AMAZON)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_AMAZON;
			} else if (storeNameOpenIAB.equals(OpenIabHelper.NAME_SAMSUNG)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_SAMSUNG;
			} else if (storeNameOpenIAB.equals(OpenIabHelper.NAME_NOKIA)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_NOKIA;
			} else if (storeNameOpenIAB.equals(OpenIabHelper.NAME_SLIDEME)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_SLIDEME;
			} else if (storeNameOpenIAB.equals(OpenIabHelper.NAME_APTOIDE)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_APTOIDE;
			} else if (storeNameOpenIAB.equals(OpenIabHelper.NAME_APPLAND)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_APPLAND;
			} else if (storeNameOpenIAB.equals(OpenIabHelper.NAME_YANDEX)) {
				return PurchaseManagerConfig.STORE_NAME_ANDROID_YANDEX;
			} else {
				// we should get here: the correct store should always be mapped!
				Log.e(TAG, "Store name could not be mapped: " + storeNameOpenIAB);
				return storeNameOpenIAB;
			}
		}
	}

	private String storeNameToOpenIAB (String storeName) {
		if (storeName == null) {
			return null;
		} else {
			if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_GOOGLE)) {
				return OpenIabHelper.NAME_GOOGLE;
			} else if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_AMAZON)) {
				return OpenIabHelper.NAME_AMAZON;
			} else if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_SAMSUNG)) {
				return OpenIabHelper.NAME_SAMSUNG;
			} else if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_NOKIA)) {
				return OpenIabHelper.NAME_NOKIA;
			} else if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_SLIDEME)) {
				return OpenIabHelper.NAME_SLIDEME;
			} else if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_APTOIDE)) {
				return OpenIabHelper.NAME_APTOIDE;
			} else if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_APPLAND)) {
				return OpenIabHelper.NAME_APPLAND;
			} else if (storeName.equals(PurchaseManagerConfig.STORE_NAME_ANDROID_YANDEX)) {
				return OpenIabHelper.NAME_YANDEX;
			} else {
				// we should get here: the correct store should always be mapped!
				Log.e(TAG, "Store name could not be mapped: " + storeName);
				return storeName;
			}
		}
	}

	private void refreshSKUs() {
  		// refresh the identifiers/SKUs: ensures we have the correct mapping in case PurchaseManagerConfig was updated
		if (config == null) return;
  		for (int i = 0; i < config.getOfferCount(); i++) {
			Offer offer = config.getOffer(i);

			// map store-specific identifiers with our default identifier!
			String identifier = offer.getIdentifier();
			Set<Map.Entry<String, String>> identifierForStores = offer.getIdentifierForStores();
			for (Map.Entry<String, String> entry : identifierForStores) {
				String storeNameOpenIAB = storeNameToOpenIAB(entry.getKey());
				String identifierForStore = entry.getValue();
				if (!(SkuManager.getInstance().getStoreSku(storeNameOpenIAB, identifier).equals(identifierForStore))) {
					SkuManager.getInstance().mapSku(identifier, storeNameOpenIAB, identifierForStore);
				}
			}
		}
	}

	@Override
	public void install (final PurchaseObserver observer, final PurchaseManagerConfig config, final boolean autoFetchInformation) {
		this.observer = observer;
		this.config = config;
		this.autoFetchInformation = autoFetchInformation;

		// build the OpenIAB options. Pass in the storeKeys as follows:
		// -------------------------------------------------------------------------
		// config.addStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_GOOGLE, "<store key GooglePlay>");
		// config.addStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_AMAZON, "<store key Amazon>");
		// ...
		// -------------------------------------------------------------------------
		Map<String, String> storeKeys = new HashMap<String, String>(16);
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_GOOGLE) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_GOOGLE),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_GOOGLE));
		}
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_AMAZON) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_AMAZON),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_AMAZON));
		}
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_SAMSUNG) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_SAMSUNG),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_SAMSUNG));
		}
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_NOKIA) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_NOKIA),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_NOKIA));
		}
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_SLIDEME) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_SLIDEME),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_SLIDEME));
		}
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_APTOIDE) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_APTOIDE),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_APTOIDE));
		}
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_APPLAND) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_APPLAND),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_APPLAND));
		}
		if (config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_YANDEX) != null) {
			storeKeys.put(storeNameToOpenIAB(PurchaseManagerConfig.STORE_NAME_ANDROID_YANDEX),
				(String)config.getStoreParam(PurchaseManagerConfig.STORE_NAME_ANDROID_YANDEX));
		}
		final OpenIabHelper.Options.Builder builder = new OpenIabHelper.Options.Builder();
		builder.setVerifyMode(OpenIabHelper.Options.VERIFY_SKIP);
		builder.setStoreSearchStrategy(OpenIabHelper.Options.SEARCH_STRATEGY_INSTALLER_THEN_BEST_FIT);
		builder.addStoreKeys(storeKeys);

		// refresh the SKUs list
		refreshSKUs();

		// start OpenIAB (needs to be run on UI-thread for some reason!?)
		activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            helper = new OpenIabHelper(activity, builder.build());
            helper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished (IabResult result) {
                    if (!result.isSuccess()) {
                        // error setting up the
                        helper = null;
                        inventory = null;

                        // remove observer and config as well
                        PurchaseManagerAndroidOpenIAB.this.observer = null;
                        PurchaseManagerAndroidOpenIAB.this.config = null;

                        // notify about the problem
                        observer.handleInstallError(new RuntimeException("Problem setting up in-app billing: " + result));
                    } else {
                        // do a restore first to get the inventory
						if (helper != null) {
							boolean querySkuDetails = autoFetchInformation; // --> that way we get prices and title/description as well!
							List<String> inappSkus = getIdsForEntitlementsAndConsumables();
							List<String> subsSkus = getIdsForSubscriptions();
							helper.queryInventoryAsync(querySkuDetails, inappSkus, subsSkus,
								new IabHelper.QueryInventoryFinishedListener() {
								@Override
								public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
									// store the inventory so we can lookup prices later!
									PurchaseManagerAndroidOpenIAB.this.inventory = inventory;

									// notify of successful initialization
									observer.handleInstall();
								}
							});
						} else {
							// can happen because of some race conditions seems
							Log.e(TAG, "Setup is finished but helper is null");
						}
                    }
                }
            });
          }
        });
	}

	@Override
	public boolean installed () {
		return helper != null;
	}

	@Override
	public void dispose () {
		// dispose OpenIAB and underlying store
		if (helper != null) {
			helper.dispose();
			helper = null;
			inventory = null;

			// remove observer and config as well
			observer = null;
			config = null;
		}
	}

	@Override
	public void purchase (final String identifier) {
        // refresh the SKUs list
        refreshSKUs();

		// purchase if we have a helper
		if (helper != null) {
			String payload = null;

			IabHelper.OnIabPurchaseFinishedListener listener = new IabHelper.OnIabPurchaseFinishedListener() {
				@Override
				public void onIabPurchaseFinished (IabResult result, Purchase purchase) {
					if (result.isFailure()) {
						// the purchase has failed

						if (result.getResponse() == IabHelper.IABHELPER_USER_CANCELLED) {
							observer.handlePurchaseCanceled();
						} else {
							observer.handlePurchaseError(new RuntimeException(result.toString()));
						}
					} else {
						// parse transaction data
						Transaction transaction = transaction(purchase);

						// forward result to listener
						observer.handlePurchase(transaction);

						// if the listener doesn't throw an error, we consume as needed
						Offer offer = config.getOffer(purchase.getSku());
						if (offer == null) {
							Log.d(TAG, "Offer not found for: " + purchase.getSku());
						} else if (offer.getType() == OfferType.CONSUMABLE) {
							// it's a consumable, so we consume right away!
							helper.consumeAsync(purchase, new IabHelper.OnConsumeFinishedListener() {
								@Override
								public void onConsumeFinished (Purchase purchase, IabResult result) {
									if (!result.isSuccess()) {
										// FIXME: if consume fails, the purchase manager should take note and
										//        try to consume again at a later point in time...
										Log.e(TAG, "Error while consuming: " + result);
									}
								}
							});
						}
					}
				}
			};
			
			Offer offer = config.getOffer(identifier);
			if (offer != null && offer.getType() == OfferType.SUBSCRIPTION)
				helper.launchSubscriptionPurchaseFlow(activity, identifier, requestCode, listener, payload);
			else
				helper.launchPurchaseFlow(activity, identifier, IabHelper.ITEM_TYPE_INAPP, requestCode, listener, payload);
		}
		else {
			Log.e(TAG, "ERROR: purchase(): openIAB helper == null");
		}
	}

	@Override
	public void purchaseRestore () {
        // refresh the SKUs list
        refreshSKUs();

        // restore if we have a helper
		if (helper != null) {
			// ask for purchase restore
			boolean querySkuDetails = autoFetchInformation; // --> that way we get prices and title/description as well!
			List<String> inappSkus = getIdsForEntitlementsAndConsumables();
			List<String> subsSkus = getIdsForSubscriptions();
			helper.queryInventoryAsync(querySkuDetails, inappSkus, subsSkus,
				new IabHelper.QueryInventoryFinishedListener() {
				@Override
				public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
					if (result.isSuccess()) {
						// store the inventory so we can lookup prices later!
						PurchaseManagerAndroidOpenIAB.this.inventory = inventory;

						// build list of purchases
						List<Purchase> purchases = inventory.getAllPurchases();
						List<Transaction> transactions = new ArrayList<Transaction>(purchases.size());
						for (int i = 0; i < purchases.size(); i++) {
							transactions.add(transaction(purchases.get(i)));
						}

						// send inventory to observer
						observer.handleRestore(transactions.toArray(new Transaction[transactions.size()]));

						// if the observer above didn't throw an error, we consume all consumeables as needed
						for (int i = 0; i < purchases.size(); i++) {
							Purchase purchase = purchases.get(i);
							Offer offer = config.getOffer(purchase.getSku());
							if (offer == null) {
								Log.d(TAG, "Offer not found for: " + purchase.getSku());
							} else if (offer.getType() == OfferType.CONSUMABLE) {
								// it's a consumable, so we consume right away!
								helper.consumeAsync(purchase, new IabHelper.OnConsumeFinishedListener() {
									@Override
									public void onConsumeFinished(Purchase purchase, IabResult result) {
										if (!result.isSuccess()) {
											// NOTE: we should only rarely have an exception due to e.g. network outages etc.
											Log.e(TAG, "Error while consuming: " + result);
										}
									}
								});
							}
						}
					} else {
						observer.handleRestoreError(new RuntimeException(result.toString()));
					}
				}
			});
		}
		else {
			Log.e(TAG, "ERROR: purchaseRestore(): openIAB helper == null");
		}
	}

	@Override
    public Information getInformation(String identifier) {
        SkuDetails details = inventory.getSkuDetails(identifier);
        if(details != null) {
            Information i = new Information(details.getTitle(), details.getDescription(), details.getPrice());
            return i;
        }
        return Information.UNAVAILABLE;
    }

    /** Converts a purchase to our transaction object. */
	Transaction transaction (Purchase purchase) {
		// obtain the SKU details (could be null!)
		SkuDetails skuDetails = inventory.getSkuDetails(purchase.getSku());

		// build the transaction from the purchase object
		Transaction transaction = new Transaction();
		transaction.setIdentifier(purchase.getSku());

		transaction.setStoreName(storeNameFromOpenIAB(purchase.getAppstoreName()));
		transaction.setOrderId(purchase.getOrderId());

		transaction.setPurchaseTime(new Date(purchase.getPurchaseTime()));
		transaction.setPurchaseText((skuDetails != null ? "Purchased " + skuDetails.getTitle() + " for " + skuDetails.getPrice() + ": " + skuDetails.getDescription()
		                                                : "Purchased"));
		transaction.setPurchaseCost(-1);   // -1 for Unknow: FIXME: need parser for cost (amount and currency code!)
		transaction.setPurchaseCostCurrency(null);

		if (purchase.getPurchaseState() != 0) {
			// order has been refunded or cancelled
			transaction.setReversalTime(new Date()); // TODO: Amazon IAP 2.0 has "cancelDate" which can be obtained via original Json
			transaction.setReversalText(purchase.getPurchaseState() == 1 ? "Cancelled" : "Refunded");
		} else {
			// still valid!
			transaction.setReversalTime(null);
			transaction.setReversalText(null);
		}

		transaction.setTransactionData(purchase.getOriginalJson());
		transaction.setTransactionDataSignature(purchase.getSignature());
		return transaction;
	}

	private List<String> getIdsForEntitlementsAndConsumables() {
		final List<String> result = new ArrayList<String>();
		final int offerCount = config.getOfferCount();
		for (int i = 0; i < offerCount; i++) {
			Offer offer = config.getOffer(i);
			if (offer.getType() == OfferType.ENTITLEMENT || offer.getType() == OfferType.CONSUMABLE) {
				result.add(offer.getIdentifier());
			}
		}
		return result;
	}

	private List<String> getIdsForSubscriptions() {
		final List<String> result = new ArrayList<String>();
		final int offerCount = config.getOfferCount();
		for (int i = 0; i < offerCount; i++) {
			Offer offer = config.getOffer(i);
			if (offer.getType() == OfferType.SUBSCRIPTION) {
				result.add(offer.getIdentifier());
			}
		}
		return result;
	}

	public void onActivityResult (int requestCode, int resultCode, Intent data) {
		// forwards activities to OpenIAB for processing
		helper.handleActivityResult(requestCode, resultCode, data);
	}

	@Override
	public String toString () {
		return "OpenIAB/" + helper.getConnectedAppstoreName();
	}
}
