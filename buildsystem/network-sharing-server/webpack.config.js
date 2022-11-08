var path = require("path");
const CopyPlugin = require("copy-webpack-plugin");

module.exports = [{
  mode: "production",
  entry: ['./scss/app.scss', './js/app.js'],
  output: {
    // This is necessary for webpack to compile
    // But we never use style-bundle.js
    filename: 'style-bundle.js',
    path: path.join(__dirname, "../../application/webserver/assets/web/public"),
    assetModuleFilename: 'images/[name][ext]'
  },
  plugins: [
      new CopyPlugin({
        patterns: [
          { from: "asset/static", to: "" },
          { from: "asset/templates", to: "" },
        ],
      }),
    ],
  module: {
    rules: [
      {
        test: /\.scss$/,
        use: [
          {
            loader: 'file-loader',
            options: {
              name: 'bundle.css',
            },
          },
          {
            loader: "extract-loader",
            options: {
              publicPath: path.join(__dirname, "public"),
            }
          },
          {
            loader: 'css-loader',
            options: {
              url: false
            }
          },
          {
            loader: 'sass-loader',
            options: {
              // Prefer Dart Sass
              implementation: require('sass'),

              // See https://github.com/webpack-contrib/sass-loader/issues/804
              webpackImporter: false,
              sassOptions: {
                includePaths: ['./node_modules']
              },
            }
          }
        ]
      },
      {
        test: /\.html$/i,
        type: 'asset/resource',
        generator: {
          filename: '[name][ext]'
        }
      },
      {
        test: /\.(png|svg|jpg|jpeg|gif|ico)$/i,
        type: 'asset/resource',
      },
    ]
  },
}];