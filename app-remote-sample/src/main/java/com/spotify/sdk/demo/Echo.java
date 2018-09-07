package com.spotify.sdk.demo;

import com.spotify.protocol.types.Item;

public interface Echo {

    class Request implements Item {
        public final String request;

        public Request(String request) {
            this.request = request;
        }

        /* For GSON */
        public Request() {
            request = null;
        }
    }

    class Response implements Item {
        public final String response;

        public Response(String response) {
            this.response = response;
        }

        /* For GSON */
        public Response() {
            response = null;
        }
    }
}
