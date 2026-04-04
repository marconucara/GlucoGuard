const username = "nuky1989@gmail.com";
const password = "010101010101";
const BASE_URL = "https://api-eu.libreview.io";

const https = require("https");
const { URL } = require("url");

const crypto = require("crypto");

function fetch(url, options = {}) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    const method = options.method || "GET";
    const headers = {};
    if (options.headers) {
      // Copia le proprietà a mano
      for (const key in options.headers) {
        if (Object.prototype.hasOwnProperty.call(options.headers, key)) {
          headers[key] = options.headers[key];
        }
      }
    }
    if (options.body) {
      headers["Content-Length"] = Buffer.byteLength(options.body);
    }

    const req = https.request(
      {
        hostname: urlObj.hostname,
        port: urlObj.port || 443,
        path: urlObj.pathname + urlObj.search,
        method: method,
        headers: headers,
      },
      function (res) {
        let data = "";
        res.on("data", function (chunk) {
          data += chunk;
        });
        res.on("end", function () {
          resolve({
            ok: res.statusCode >= 200 && res.statusCode < 300,
            status: res.statusCode,
            statusText: res.statusMessage,
            headers: res.headers,
            text: function () {
              return Promise.resolve(data);
            },
            json: function () {
              return Promise.resolve(JSON.parse(data));
            },
          });
        });
      }
    );
    req.on("error", reject);
    if (options.body) req.write(options.body);
    req.end();
  });
}

module.exports.fetchGlucose = async function fetchGlucose() {
  // 1. Autenticazione
  const loginRes = await fetch(`${BASE_URL}/auth/login`, {
    method: "POST",
    headers: {
      "User-Agent": "LibreLinkUp/4.16.0 CFNetwork/1485 Darwin/23.1.0",
      product: "llu.ios",
      version: "4.16.0",
      Accept: "application/json",
      Pragma: "no-cache",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      email: username,
      password: password,
    }),
  });

  // per debug only:
  // const loginText = await loginRes.text();
  // console.log(loginText)
  
  if (!loginRes.ok) throw new Error("Auth failed");
  const loginData = await loginRes.json();
  const token =
    loginData &&
    loginData.data &&
    loginData.data.authTicket &&
    loginData.data.authTicket.token;
  const accountId =
    loginData &&
    loginData.data &&
    loginData.data.user &&
    loginData.data.user.id;

  if (!token || !accountId) throw new Error("Missing token or accountId");

  // 2. Prendi connectionId
  const connRes = await fetch(`${BASE_URL}/llu/connections`, {
    headers: {
      "User-Agent": "LibreLinkUp/4.16.0 CFNetwork/1485 Darwin/23.1.0",
      product: "llu.ios",
      version: "4.16.0",
      Accept: "application/json",
      Pragma: "no-cache",
      Authorization: `Bearer ${token}`,
      "Account-Id": crypto
        .createHash("sha256")
        .update(accountId, "utf8")
        .digest("hex"),
    },
  });
  console.log("Connection response status:", connRes);
  if (!connRes.ok) throw new Error("Get connectionId failed");
  const connData = await connRes.json();
  const connectionId =
    connData && connData.data && connData.data[0] && connData.data[0].patientId;
  if (!connectionId) throw new Error("Missing connectionId");

  // 3. Prendi glucosio
  const graphRes = await fetch(
    `${BASE_URL}/llu/connections/${connectionId}/graph`,
    {
      headers: {
        "User-Agent": "LibreLinkUp/4.16.0 CFNetwork/1485 Darwin/23.1.0",
        product: "llu.ios",
        version: "4.16.0",
        Accept: "application/json",
        Pragma: "no-cache",
        Authorization: `Bearer ${token}`,
        "Account-Id": crypto
          .createHash("sha256")
          .update(accountId, "utf8")
          .digest("hex"),
      },
    }
  );
  if (!graphRes.ok) throw new Error("Get glucose failed");
  const graphData = await graphRes.json();
  const glucoseValue =
    graphData &&
    graphData.data &&
    graphData.data.connection &&
    graphData.data.connection.glucoseMeasurement &&
    graphData.data.connection.glucoseMeasurement.ValueInMgPerDl;

  console.log("Glucose value:", glucoseValue);
  return glucoseValue;
};

module.exports.fetchGlucose();
