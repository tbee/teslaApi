package org.tbee.tesla.login.javafx;

/*-
 * #%L
 * TeslaAPIJavafxLogin
 * %%
 * Copyright (C) 2020 - 2021 Tom Eugelink
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class TeslaAPIJavafxLogin extends Application {

    public static void main(String[] args) {    	
        launch();
    }

    @Override
    public void start(Stage stage) {
    	
//        new SimpleProxyServer().runServer("tesla.com", 80, 8080);
//        new SimpleProxyServer().runServer("tesla.com", 443, 8443); // no use doing this for https, it's encrypted
				
    	WebView webView = new WebView();    	
    	WebEngine engine = webView.getEngine();
    	engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                System.out.println("SUCCEEDED Location="  + engine.getLocation());
            }
        });
		engine.load("https://tesla.com");
        Scene scene = new Scene(new StackPane(webView), 1600, 1200);
        stage.setScene(scene);
        stage.show();
    }
}
